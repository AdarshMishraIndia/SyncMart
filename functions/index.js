// New Cloud Fucntion for SyncMart with the following

// when a new list(new document) is created in collection shopping_lists,
// then in that document whoever(email) is in accessEmail will get a notification with
// Title: Welcome to <listName>!
// Body: You have been invited to be a member by <owner.name>

// when a new item is added to a list
// whoever is in accessEmails and owner - item.addedBy will get a notification with
// Title: New item(s) in <listName>
// Body: <addedBy.name> added <no. of items added>

// when an item element is modified, means its pending field is changed from true to false
// whoever is in accessEmails and owner - item.addedBy will get a notification with
// Title: Finished item in <listName>
// Body: <addedBy.name> marked <item.name> as "finished"

// when an item element is deleted,
// whoever is in accessEmails and owner - item.addedBy of the deleted item will get a notification with 
// Title: Deleted item(s) in <listName>
// Body: <addedBy.name> deleted <no of items deleted>

// when an item element is modified, means its important field is changed from false to true
// whoever is in accessEmails and owner - item.addedBy will get a notification with 
// Title: Important item in <listName>
// Body: <addedBy.name> marked <item.name> as "IMPORTANT"

// in an event a list.owner changes then notify the new owner with
// Title: Ownership Changed of <listName>
// Body: You are the new owner of <listName></listName>

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const { sendNotificationToEmails } = require("./helpers");

if (!admin.apps.length) {
  admin.initializeApp();
}

// --- Notification batching state ---
const notificationBuffer = new Map(); // key: listId, value: {added:[], deleted:[], finished:[], important:[], timer:Timeout}
const FLUSH_DELAY_MS = 5000;

exports.onShoppingListUpdate = onDocumentUpdated("shopping_lists/{listId}", async (event) => {
  const listId = event.params.listId;
  const beforeExists = event.data.before.exists;
  const afterExists = event.data.after.exists;
  const beforeData = beforeExists ? event.data.before.data() : {};
  const afterData = afterExists ? event.data.after.data() : {};
  const listName = afterData.listName || "Shopping List";
  const actorEmail = event.auth?.token?.email || null;

  const beforeItems = beforeData.items || {};
  const afterItems = afterData.items || {};

  // --- Helper: Compare two item objects by relevant fields ---
  function areItemsEqual(a, b) {
    return a.name === b.name &&
      a.pending === b.pending &&
      a.important === b.important &&
      a.addedBy === b.addedBy;
  }

  const addedItemKeys = Object.keys(afterItems).filter(k => !(k in beforeItems));
  const deletedItemKeys = Object.keys(beforeItems).filter(k => !(k in afterItems));
  const modifiedItemKeys = Object.keys(beforeItems).filter(
    k => (k in afterItems) && !areItemsEqual(beforeItems[k], afterItems[k])
  );

  const emailsToLookup = new Set([actorEmail, afterData.owner]);
  (afterData.accessEmails || []).forEach(e => e && emailsToLookup.add(e));
  addedItemKeys.forEach(k => afterItems[k]?.addedBy && emailsToLookup.add(afterItems[k].addedBy));
  modifiedItemKeys.forEach(k => afterItems[k]?.addedBy && emailsToLookup.add(afterItems[k].addedBy));

  // --- Display name cache & lookup ---
  const nameCache = new Map();
  async function getUserName(email) {
    if (!email) return "Someone";
    if (nameCache.has(email)) return nameCache.get(email);
    try {
      const record = await admin.auth().getUserByEmail(email);
      const displayName = record.displayName || "Someone";
      nameCache.set(email, displayName);
      return displayName;
    } catch (err) {
      console.error(`Error fetching user name for ${email}:`, err);
      nameCache.set(email, "Someone");
      return "Someone";
    }
  }
  await Promise.all([...emailsToLookup].map(getUserName));
  const nameOf = email => nameCache.get(email) || "Someone";

  const listMembers = Array.from(new Set([...(afterData.accessEmails || []), afterData.owner].filter(Boolean)));

  // --- Immediate notification for new list creation ---
  if (!beforeExists && afterExists) {
    const ownerName = nameOf(afterData.owner);
    await sendNotificationToEmails(
      (afterData.accessEmails || []),
      `Welcome to ${listName}!`,
      `You have been invited to be a member by ${ownerName}.`
    );
    return;
  }

  // --- Collect changes for batching ---
  if (!notificationBuffer.has(listId)) {
    notificationBuffer.set(listId, {
      added: [],
      deleted: [],
      finished: [],
      important: [],
      actorEmail,
      listMembers,
      listName,
      timer: null
    });
  }
  const buffer = notificationBuffer.get(listId);

  // Add new items
  if (addedItemKeys.length > 0) {
    buffer.added.push(...addedItemKeys.map(k => ({
      name: afterItems[k].name,
      addedBy: afterItems[k].addedBy || actorEmail
    })));
  }

// --- Add deleted items (only where pending === true)
if (deletedItemKeys.length > 0) {
  deletedItemKeys.forEach(k => {
    const item = beforeItems[k];
    if (item.pending === true) {  // ✅ CHANGED: now we notify only for pending items
      buffer.deleted.push({ name: item.name, deletedBy: actorEmail });
    }
  });
}

  // Add modified items
  modifiedItemKeys.forEach(k => {
    const beforeItem = beforeItems[k];
    const afterItem = afterItems[k];
    const modifiedBy = afterItem?.addedBy || actorEmail;

    if (beforeItem.pending !== afterItem.pending && afterItem.pending === false) {
      buffer.finished.push({ name: afterItem.name, modifiedBy });
    }
    if (beforeItem.important !== afterItem.important && afterItem.important === true) {
      buffer.important.push({ name: afterItem.name, modifiedBy });
    }
  });

  // Ownership change is a single event -> send immediately
  if (beforeData.owner !== afterData.owner && afterData.owner && afterData.owner !== actorEmail) {
    await sendNotificationToEmails(
      [afterData.owner],
      `Ownership Changed of ${listName}`,
      `You are the new owner of ${listName}.`
    );
  }

  // --- Schedule batch flush if not already scheduled ---
  if (!buffer.timer) {
    buffer.timer = setTimeout(async () => {
      await flushNotifications(listId);
    }, FLUSH_DELAY_MS);
  }

  async function flushNotifications(listId) {
    const buf = notificationBuffer.get(listId);
    if (!buf) return;

    const { added, deleted, finished, important, actorEmail, listMembers, listName } = buf;

    async function notifyAllExcept(recipients, title, message, excludeEmail) {
      const filtered = recipients.filter(e => e && e !== excludeEmail);
      if (filtered.length) {
        await sendNotificationToEmails(filtered, title, message);
      }
    }

    if (added.length > 0) {
      const addedBy = added[0].addedBy;
      await notifyAllExcept(
        listMembers,
        `New item(s) in ${listName}`,
        `${nameOf(addedBy)} added ${added.length} new item${added.length > 1 ? "s" : ""}.`,
        addedBy
      );
    }

    if (deleted.length > 0) {
      await notifyAllExcept(
        listMembers,
        `Deleted item(s) in ${listName}`,
        `${nameOf(actorEmail)} deleted ${deleted.length} item${deleted.length > 1 ? "s" : ""}.`,
        actorEmail
      );
    }

if (finished.length > 0) {
  // Group finished items by modifier so we can send one notification per user per batch
  const groupedByUser = finished.reduce((acc, f) => {
    if (!acc[f.modifiedBy]) acc[f.modifiedBy] = [];
    acc[f.modifiedBy].push(f.name);
    return acc;
  }, {});

  for (const [modifiedBy, itemNames] of Object.entries(groupedByUser)) {
    await notifyAllExcept(
      listMembers,
      `Finished item(s) in ${listName}`,
      `${nameOf(modifiedBy)} marked ${itemNames.length} item${itemNames.length > 1 ? "s" : ""} as finished.`,
      modifiedBy
    );
  }
}

if (important.length > 0) {
  // Group important items by modifier (like finished items)
  const groupedByUser = important.reduce((acc, imp) => {
    if (!acc[imp.modifiedBy]) acc[imp.modifiedBy] = [];
    acc[imp.modifiedBy].push(imp.name);
    return acc;
  }, {});

  for (const [modifiedBy, itemNames] of Object.entries(groupedByUser)) {
    await notifyAllExcept(
      listMembers,
      `Important item(s) in ${listName}`,
      `${nameOf(modifiedBy)} marked ${itemNames.length} item${itemNames.length > 1 ? "s" : ""} as IMPORTANT.`,
      modifiedBy
    );
  }
}

    notificationBuffer.delete(listId);
  }
});