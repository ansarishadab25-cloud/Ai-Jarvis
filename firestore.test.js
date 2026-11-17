const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write profiles or commands", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      preferredLanguageCode: "hi-IN",
      handsFreeEnabled: false,
      historyEnabled: true,
      speechRate: 1.0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("commands").get()
  );
});

test("Authenticated user can create and partially update own profile, but cannot tamper immutable fields or inject ghost keys", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Shadab",
      preferredLanguageCode: "hi-IN",
      handsFreeEnabled: false,
      historyEnabled: true,
      speechRate: 1.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguageCode: "hi-en",
      handsFreeEnabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid language enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguageCode: "fr-FR",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      handsFreeEnabled: false,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Authenticated user can create, list, and delete own command history; rejects extra id field and updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cmdRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("commands")
    .doc("cmd_1");

  await assertSucceeds(
    cmdRef.set({
      userId: ALICE_UID,
      query: "सुबह 7 बजे का अलार्म लगाओ",
      response: "सर, मैंने 07:00 बजे का अलार्म सेट कर दिया है।",
      intentType: "Alarm",
      language: "hi-IN",
      status: "COMPLETED",
      createdAt: new Date(Date.now() - 2000),
    })
  );

  // List subcollection succeeds (empty or populated)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("commands").get()
  );

  // Reject `@DocumentId` id field inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("commands")
      .doc("cmd_2")
      .set({
        id: "cmd_2",
        userId: ALICE_UID,
        query: "5 minute timer",
        response: "Started timer",
        intentType: "Timer",
        language: "hi-en",
        status: "COMPLETED",
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Reject immutable command update
  await assertFails(cmdRef.update({ query: "tampered" }));

  // Owner can delete command
  await assertSucceeds(cmdRef.delete());
});

test("Cross-user isolation: Alice cannot read, list, create, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const past = new Date(Date.now() - 10000);
    await adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      preferredLanguageCode: "en-IN",
      handsFreeEnabled: false,
      historyEnabled: true,
      speechRate: 1.0,
      createdAt: past,
      updatedAt: past,
    });
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("commands")
      .doc("bob_cmd")
      .set({
        userId: BOB_UID,
        query: "Open settings",
        response: "Opening settings",
        intentType: "Settings",
        language: "en-IN",
        status: "COMPLETED",
        createdAt: past,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("commands").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("commands")
      .doc("bob_cmd")
      .delete()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("commands")
      .doc("spoof")
      .set({
        userId: ALICE_UID,
        query: "Spoof",
        response: "No",
        intentType: "Chat",
        language: "hi-IN",
        status: "COMPLETED",
        createdAt: new Date(Date.now() - 1000),
      })
  );
});
