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

exports.onShoppingListUpdate = onDocumentUpdated("shopping_lists/{listId}", async (event) => {
  // --- Extract snapshot data ---
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

  // --- Identify changes ---
  const addedItemKeys   = Object.keys(afterItems).filter(k => !(k in beforeItems));
  const deletedItemKeys = Object.keys(beforeItems).filter(k => !(k in afterItems));
  const modifiedItemKeys = Object.keys(beforeItems).filter(
    k => (k in afterItems) && !areItemsEqual(beforeItems[k], afterItems[k])
  );

  // --- Collect all unique emails that we need display names for ---
  const emailsToLookup = new Set([actorEmail, afterData.owner]);
  (afterData.accessEmails || []).forEach(email => email && emailsToLookup.add(email));
  addedItemKeys.forEach(k => afterItems[k]?.addedBy && emailsToLookup.add(afterItems[k].addedBy));
  modifiedItemKeys.forEach(k => (afterItems[k]?.addedBy || actorEmail) && emailsToLookup.add(afterItems[k]?.addedBy || actorEmail));

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

  // Pre-fetch all display names in parallel
  await Promise.all([...emailsToLookup].map(getUserName));

  // --- Helper: Fast name lookup from cache ---
  const nameOf = email => nameCache.get(email) || "Someone";

  // --- Helper: Send notification to all except one excluded email ---
  async function notifyAllExcept(recipients, title, message, excludeEmail) {
    const filteredRecipients = recipients.filter(e => e && e !== excludeEmail);
    if (!filteredRecipients.length) return;
    try {
      await sendNotificationToEmails(filteredRecipients, title, message);
    } catch (err) {
      console.error("Notification error:", err);
    }
  }

  // --- Build recipient list (owner + shared users) ---
  const listMembers = Array.from(new Set([...(afterData.accessEmails || []), afterData.owner].filter(Boolean)));

  // --- 1) Handle new list creation ---
  if (!beforeExists && afterExists) {
    const ownerName = nameOf(afterData.owner);
    await notifyAllExcept(
      afterData.accessEmails || [],
      `Welcome to ${listName}!`,
      `You have been invited to be a member by ${ownerName}.`,
      actorEmail
    );
    return; // Nothing else to do for a brand-new list
  }

  // --- 2) Handle added items ---
  if (addedItemKeys.length > 0) {
    const addedBy = afterItems[addedItemKeys[0]]?.addedBy || actorEmail;
    await notifyAllExcept(
      listMembers,
      `New item(s) in ${listName}`,
      `${nameOf(addedBy)} added ${addedItemKeys.length} new item${addedItemKeys.length > 1 ? "s" : ""}.`,
      addedBy
    );
  }

  // --- 3) Handle deleted items ---
  if (deletedItemKeys.length > 0) {
    await notifyAllExcept(
      listMembers,
      `Deleted item(s) in ${listName}`,
      `${nameOf(actorEmail)} deleted ${deletedItemKeys.length} item${deletedItemKeys.length > 1 ? "s" : ""}.`,
      actorEmail
    );
  }

  // --- 4) Handle modified items (pending/important changes) ---
  for (const key of modifiedItemKeys) {
    const beforeItem = beforeItems[key];
    const afterItem = afterItems[key];
    const modifiedBy = afterItem?.addedBy || actorEmail;
    const modifierName = nameOf(modifiedBy);

    if (beforeItem.pending !== afterItem.pending && afterItem.pending === false) {
      await notifyAllExcept(
        listMembers,
        `Finished item in ${listName}`,
        `${modifierName} marked ${afterItem.name} as "finished".`,
        modifiedBy
      );
    }

    if (beforeItem.important !== afterItem.important && afterItem.important === true) {
      await notifyAllExcept(
        listMembers,
        `Important item in ${listName}`,
        `${modifierName} marked ${afterItem.name} as "IMPORTANT".`,
        modifiedBy
      );
    }
  }

  // --- 5) Handle ownership transfer ---
  if (beforeData.owner !== afterData.owner && afterData.owner && afterData.owner !== actorEmail) {
    await sendNotificationToEmails(
      [afterData.owner],
      `Ownership Changed of ${listName}`,
      `You are the new owner of ${listName}.`
    );
  }
});
