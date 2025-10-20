const admin = require("firebase-admin");

// Ensure admin is initialized (safe whether init runs here or in index)
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Fetch deduplicated FCM tokens for an array of user emails
 * @param {string[]} emails
 * @returns {Promise<string[]>}
 */
async function getTokensForEmails(emails = []) {
  if (!emails || !emails.length) return [];

  const refs = emails.map(email => db.collection("Users").doc(email));
  const docs = await db.getAll(...refs);
  const tokens = [];

  docs.forEach(doc => {
    if (doc.exists) {
      const data = doc.data();
      if (Array.isArray(data.fcmTokens)) {
        tokens.push(...data.fcmTokens);
      }
    }
  });

  // dedupe and remove falsy
  return Array.from(new Set(tokens.filter(Boolean)));
}

/**
 * Send a notification to given user emails (handles 500-token limit)
 * @param {string[]} emails
 * @param {string} title
 * @param {string} body
 * @param {string} deepLink - optional, defaults to dashboard
 */
async function sendNotificationToEmails(emails = [], title, body, deepLink) {
  const tokens = await getTokensForEmails(emails);
  if (!tokens.length) return;

  const messaging = admin.messaging();
  const payload = {
    notification: { title, body },
    data: {
      deepLink: deepLink || "myapp://dashboard" // default link
    }
  };

  const promises = [];
  for (let i = 0; i < tokens.length; i += 500) {
    const chunk = tokens.slice(i, i + 500);
    promises.push(
      messaging.sendEachForMulticast({
        tokens: chunk,
        ...payload
      })
    );
  }

  return Promise.all(promises);
}

module.exports = { sendNotificationToEmails, getTokensForEmails };