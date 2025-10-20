// New Cloud Fucntion for SyncMart with the following

// when a new list(new document) is created in collection shopping_lists,
// then in that document whoever(email) is in accessEmail will get a notification with
// Title: Welcome to <listName>!
// Body: You have been added as a member by <owner.name>

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

const { onDocumentUpdated, onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const { sendNotificationToEmails } = require("./helpers");

if (!admin.apps.length) {
  admin.initializeApp();
}

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

const nameOf = (email) => nameCache.get(email) || "Someone";

// --- Helper to compare item fields ---
function areItemsEqual(a, b) {
  return a.name === b.name &&
    a.pending === b.pending &&
    a.important === b.important &&
    a.addedBy === b.addedBy;
}

// --- Helper to send notifications excluding actor and with deepLink ---
async function notifyAllExcept(recipients, title, message, excludeEmail, deepLink) {
  const filtered = recipients.filter(e => e && e !== excludeEmail);
  if (filtered.length) {
    await sendNotificationToEmails(filtered, title, message, deepLink);
  }
}

// --- Trigger for new shopping list creation ---
exports.onShoppingListCreated = onDocumentCreated("shopping_lists/{listId}", async (event) => {
  const listData = event.data.data();
  const listId = event.params.listId;
  const listName = listData.listName || "Shopping List";
  const ownerEmail = listData.owner;
  const accessEmails = listData.accessEmails || [];

  await getUserName(ownerEmail); // populate cache
  const ownerName = nameOf(ownerEmail);

  if (accessEmails.length) {
    await sendNotificationToEmails(
      accessEmails,
      `Welcome to ${listName}!`,
      `You have been added as a member by ${ownerName}.`,
      `myapp://dashboard` // dashboard link for new list
    );
  }
});

// --- Trigger for updates to shopping list ---
exports.onShoppingListUpdated = onDocumentUpdated("shopping_lists/{listId}", async (event) => {
  const listId = event.params.listId;
  const beforeData = event.data.before.exists ? event.data.before.data() : {};
  const afterData = event.data.after.exists ? event.data.after.data() : {};
  const listName = afterData.listName || "Shopping List";
  const actorEmail = event.auth?.token?.email || null;

  const beforeItems = beforeData.items || {};
  const afterItems = afterData.items || {};

  const addedItemKeys = Object.keys(afterItems).filter(k => !(k in beforeItems));
  const deletedItemKeys = Object.keys(beforeItems).filter(k => !(k in afterItems));
  const modifiedItemKeys = Object.keys(beforeItems).filter(
    k => (k in afterItems) && !areItemsEqual(beforeItems[k], afterItems[k])
  );

  // Gather all emails for name lookup
  const emailsToLookup = new Set([actorEmail, afterData.owner]);
  (afterData.accessEmails || []).forEach(e => e && emailsToLookup.add(e));
  addedItemKeys.forEach(k => afterItems[k]?.addedBy && emailsToLookup.add(afterItems[k].addedBy));
  modifiedItemKeys.forEach(k => afterItems[k]?.addedBy && emailsToLookup.add(afterItems[k].addedBy));

  await Promise.all([...emailsToLookup].map(getUserName));

  const listMembers = Array.from(new Set([...(afterData.accessEmails || []), afterData.owner].filter(Boolean)));

  const addedItems = addedItemKeys.map(k => ({ name: afterItems[k].name, addedBy: afterItems[k].addedBy || actorEmail }));
  const deletedItems = deletedItemKeys
    .map(k => beforeItems[k])
    .filter(item => item.pending === true)
    .map(item => ({ name: item.name, deletedBy: actorEmail }));

  const finishedItems = [];
  const importantItems = [];

  modifiedItemKeys.forEach(k => {
    const beforeItem = beforeItems[k];
    const afterItem = afterItems[k];
    const modifiedBy = afterItem.addedBy || actorEmail;

    if (beforeItem.pending !== afterItem.pending && afterItem.pending === false) {
      finishedItems.push({ name: afterItem.name, modifiedBy });
    }
    if (beforeItem.important !== afterItem.important && afterItem.important === true) {
      importantItems.push({ name: afterItem.name, modifiedBy });
    }
  });

  // Ownership change notification
  if (beforeData.owner !== afterData.owner && afterData.owner && afterData.owner !== actorEmail) {
    await sendNotificationToEmails(
      [afterData.owner],
      `Ownership Changed of ${listName}`,
      `You are the new owner of ${listName}.`,
      `myapp://dashboard` // dashboard link for ownership change
    );
  }

  // --- Notifications for item changes ---
  const listDeepLink = `myapp://list/${listId}`;

  // Added items
  if (addedItems.length) {
    const addedBy = addedItems[0].addedBy;
    await notifyAllExcept(
      listMembers,
      `New item(s) in ${listName}`,
      `${nameOf(addedBy)} added ${addedItems.length} new item${addedItems.length > 1 ? "s" : ""}.`,
      addedBy,
      listDeepLink
    );
  }

  // Deleted items
  if (deletedItems.length) {
    await notifyAllExcept(
      listMembers,
      `Deleted item(s) in ${listName}`,
      `${nameOf(actorEmail)} deleted ${deletedItems.length} item${deletedItems.length > 1 ? "s" : ""}.`,
      actorEmail,
      listDeepLink
    );
  }

  // Finished items
  if (finishedItems.length) {
    const groupedByUser = finishedItems.reduce((acc, f) => {
      if (!acc[f.modifiedBy]) acc[f.modifiedBy] = [];
      acc[f.modifiedBy].push(f.name);
      return acc;
    }, {});

    for (const [modifiedBy, items] of Object.entries(groupedByUser)) {
      await notifyAllExcept(
        listMembers,
        `Finished item(s) in ${listName}`,
        `${nameOf(modifiedBy)} marked ${items.length} item${items.length > 1 ? "s" : ""} as finished.`,
        modifiedBy,
        listDeepLink
      );
    }
  }

  // Important items
  if (importantItems.length) {
    const groupedByUser = importantItems.reduce((acc, imp) => {
      if (!acc[imp.modifiedBy]) acc[imp.modifiedBy] = [];
      acc[imp.modifiedBy].push(imp.name);
      return acc;
    }, {});

    for (const [modifiedBy, items] of Object.entries(groupedByUser)) {
      await notifyAllExcept(
        listMembers,
        `Important item(s) in ${listName}`,
        `${nameOf(modifiedBy)} marked ${items.length} item${items.length > 1 ? "s" : ""} as IMPORTANT.`,
        modifiedBy,
        listDeepLink
      );
    }
  }
});
