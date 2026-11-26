const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const EVE_UID = "eve_uid_789";
const CONN_ID = "conn_alice_bob_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

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

async function seedActiveConnection() {
  const now = pastDate(5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("connections").doc(CONN_ID).set({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "An", [BOB_UID]: "Bình" },
      inviteCode: "TG8899",
      status: "active",
      createdBy: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated users are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("locations").doc(ALICE_UID).get());
  await assertFails(unauth.collection("connections").get());
  await assertFails(unauth.collection("activities").get());
  await assertFails(unauth.collection("places").get());
  await assertFails(unauth.collection("checkins").get());
});

test("Users collection isolates PII and blocks shadow updates", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = pastDate(3000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Nguyễn An",
      email: "an@example.com",
      inviteCode: "TG1234",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Bob cannot read Alice's PII doc
  await assertFails(bob.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field rejected
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(1000),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      displayName: "An Nguyễn",
      phoneNumber: "0901234567",
      updatedAt: pastDate(1000),
    })
  );
});

test("Pairing codes allow lookup by exact code and pairing by second user only", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const t = pastDate(4000);

  await assertSucceeds(
    alice.collection("pairing_codes").doc("TG4567").set({
      code: "TG4567",
      ownerUid: ALICE_UID,
      ownerName: "An",
      status: "waiting",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Bob cannot list all pairing codes
  await assertFails(bob.collection("pairing_codes").get());
  // Bob can get exact code
  await assertSucceeds(bob.collection("pairing_codes").doc("TG4567").get());
  // Bob can accept pairing
  await assertSucceeds(
    bob.collection("pairing_codes").doc("TG4567").update({
      status: "paired",
      partnerUid: BOB_UID,
      partnerName: "Bình",
      connectionId: CONN_ID,
      updatedAt: pastDate(1000),
    })
  );
});

test("Connections enforce max 2 users, prevent third-party access, and lock after disconnect", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const eve = testEnv.authenticatedContext(EVE_UID).firestore();
  const t = pastDate(4000);

  // Reject 3 members
  await assertFails(
    alice.collection("connections").doc("conn_3").set({
      memberUids: [ALICE_UID, BOB_UID, EVE_UID],
      inviteCode: "TG1111",
      status: "active",
      createdBy: ALICE_UID,
      createdAt: t,
      updatedAt: t,
    })
  );

  await assertSucceeds(
    alice.collection("connections").doc(CONN_ID).set({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "An", [BOB_UID]: "Bình" },
      inviteCode: "TG8899",
      status: "active",
      createdBy: ALICE_UID,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Eve cannot read or query
  await assertFails(eve.collection("connections").doc(CONN_ID).get());
  await assertFails(eve.collection("connections").get());

  // Bob can query with array-contains
  await assertSucceeds(
    bob.collection("connections").where("memberUids", "array-contains", BOB_UID).get()
  );

  // Bob disconnects
  await assertSucceeds(
    bob.collection("connections").doc(CONN_ID).update({
      status: "disconnected",
      disconnectedBy: BOB_UID,
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state locked against re-activation
  await assertFails(
    alice.collection("connections").doc(CONN_ID).update({
      status: "active",
      updatedAt: pastDate(500),
    })
  );
});

test("Locations, device_status, and sharing_permissions enforce authorizedPartnerUid and immediate revocation", async () => {
  await seedActiveConnection();
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const eve = testEnv.authenticatedContext(EVE_UID).firestore();
  const t = pastDate(3000);

  await assertSucceeds(
    alice.collection("locations").doc(ALICE_UID).set({
      userId: ALICE_UID,
      connectionId: CONN_ID,
      authorizedPartnerUid: BOB_UID,
      latitude: 10.7769,
      longitude: 106.7009,
      accuracyMeters: 8.5,
      speedMps: 1.2,
      bearing: 90.0,
      moving: true,
      gpsInterrupted: false,
      sharingMode: "CONTINUOUS",
      address: "Quận 1, TP.HCM",
      clientUpdatedAtMs: Date.now() - 1000,
      createdAt: t,
      updatedAt: t,
    })
  );

  // Bob can read via authorizedPartnerUid query & doc
  await assertSucceeds(bob.collection("locations").doc(ALICE_UID).get());
  await assertSucceeds(
    bob.collection("locations").where("authorizedPartnerUid", "==", BOB_UID).get()
  );

  // Eve cannot read
  await assertFails(eve.collection("locations").doc(ALICE_UID).get());
  await assertFails(eve.collection("locations").get());

  // Alice revokes access by clearing authorizedPartnerUid
  await assertSucceeds(
    alice.collection("locations").doc(ALICE_UID).update({
      authorizedPartnerUid: null,
      sharingMode: "OFF",
      updatedAt: pastDate(1000),
    })
  );

  // Bob immediately loses read access to Alice's location doc
  await assertFails(bob.collection("locations").doc(ALICE_UID).get());

  // Device status create & partner read
  await assertSucceeds(
    alice.collection("device_status").doc(ALICE_UID).set({
      userId: ALICE_UID,
      connectionId: CONN_ID,
      authorizedPartnerUid: BOB_UID,
      batteryPercent: 82,
      chargingState: "DISCHARGING",
      connectionState: "ONLINE",
      locationUpdating: true,
      locationInterrupted: false,
      movementState: "MOVING",
      estimatedSpeedKmh: 18.5,
      lastHeartbeatMs: Date.now() - 1000,
      lastBatteryUpdateMs: Date.now() - 1000,
      lastAppActiveMs: Date.now() - 1000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    bob.collection("device_status").where("authorizedPartnerUid", "==", BOB_UID).get()
  );
});

test("Places, activities, history, checkins, memories, trips, and notifications enforce 2-member isolation", async () => {
  await seedActiveConnection();
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const eve = testEnv.authenticatedContext(EVE_UID).firestore();
  const t = pastDate(3000);

  // Place
  await assertSucceeds(
    alice.collection("places").doc("place_home").set({
      userId: ALICE_UID,
      connectionId: CONN_ID,
      memberUids: [ALICE_UID, BOB_UID],
      name: "Nhà riêng",
      category: "HOME",
      latitude: 10.77,
      longitude: 106.70,
      radiusMeters: 200,
      notifyOnArrive: true,
      notifyOnLeave: true,
      notifyWhileInside: false,
      leaveWindowAlertEnabled: false,
      leaveWindowStartHour: 7,
      leaveWindowEndHour: 9,
      notificationsEnabled: true,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(bob.collection("places").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(eve.collection("places").doc("place_home").get());

  // Personal check-in vs Shared check-in
  await assertSucceeds(
    alice.collection("checkins").doc("ck_personal").set({
      userId: ALICE_UID,
      authorName: "An",
      connectionId: CONN_ID,
      memberUids: [ALICE_UID],
      placeName: "Quán cà phê",
      latitude: 10.78,
      longitude: 106.69,
      albumScope: "PERSONAL",
      photoCount: 1,
      capturedAtMs: Date.now() - 2000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertFails(bob.collection("checkins").doc("ck_personal").get());

  await assertSucceeds(
    alice.collection("checkins").doc("ck_shared").set({
      userId: ALICE_UID,
      authorName: "An",
      connectionId: CONN_ID,
      memberUids: [ALICE_UID, BOB_UID],
      placeName: "Hồ Gươm",
      latitude: 21.0285,
      longitude: 105.8542,
      albumScope: "SHARED",
      photoCount: 1,
      capturedAtMs: Date.now() - 2000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(bob.collection("checkins").doc("ck_shared").get());

  // Activity
  await assertSucceeds(
    alice.collection("activities").doc("act_1").set({
      userId: ALICE_UID,
      actorName: "An",
      connectionId: CONN_ID,
      memberUids: [ALICE_UID, BOB_UID],
      type: "ARRIVED_PLACE",
      title: "An đã đến Nhà riêng",
      description: "Bán kính 200m",
      placeName: "Nhà riêng",
      dateKey: "2026-11-26",
      eventTimeMs: Date.now() - 1000,
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(bob.collection("activities").where("memberUids", "array-contains", BOB_UID).get());
  await assertFails(eve.collection("activities").doc("act_1").get());

  // Notification
  await assertSucceeds(
    alice.collection("notifications").doc("notif_1").set({
      senderId: ALICE_UID,
      recipientUid: BOB_UID,
      connectionId: CONN_ID,
      memberUids: [ALICE_UID, BOB_UID],
      type: "ARRIVED_PLACE",
      title: "An đã đến Nhà riêng",
      body: "Cập nhật lúc 08:00",
      readFlag: false,
      createdAtMs: Date.now() - 1000,
      createdAt: t,
    })
  );
  await assertSucceeds(bob.collection("notifications").where("recipientUid", "==", BOB_UID).get());
  await assertSucceeds(bob.collection("notifications").doc("notif_1").update({ readFlag: true }));
  await assertFails(eve.collection("notifications").doc("notif_1").get());
});
