const admin = require("firebase-admin");
const db = admin.firestore();

async function getUserDataMap(emails = []) {
  const unique = [...new Set(emails.filter(Boolean))];
  if (!unique.length) return new Map();

  const refs = unique.map(email => db.collection("Users").doc(email));
  const docs = await db.getAll(...refs);

  const map = new Map();
  docs.forEach((doc, i) => {
    if (doc.exists) {
      const data = doc.data();
      map.set(unique[i], {
        name: data.name || "Someone",
        // Matches the 'fcmToken' field used in the Android Service
        fcmTokens: Array.isArray(data.fcmToken) ? data.fcmToken.filter(Boolean) : [],
      });
    } else {
      map.set(unique[i], { name: "Someone", fcmTokens: [] });
    }
  });
  return map;
}

async function sendNotification(emails = [], title, body, deepLink, userDataMap, excludeEmails = []) {
  const excludeSet = new Set(excludeEmails.filter(Boolean));
  const tokens = [];

  for (const email of emails) {
    if (!email || excludeSet.has(email)) continue;
    const user = userDataMap.get(email);
    if (user?.fcmTokens?.length) tokens.push(...user.fcmTokens);
  }

  const uniqueTokens = [...new Set(tokens)];
  if (!uniqueTokens.length) return;

  const messaging = admin.messaging();
  const payload = {
    notification: { title, body },
    // Data payload mirrors notification for foreground handling
    data: { 
      title, 
      message: body, 
      deepLink: deepLink || "syncmart://dashboard" 
    },
  };

  const promises = [];
  for (let i = 0; i < uniqueTokens.length; i += 500) {
    promises.push(
      messaging.sendEachForMulticast({ 
        tokens: uniqueTokens.slice(i, i + 500), 
        ...payload 
      })
    );
  }
  return Promise.all(promises);
}

module.exports = { getUserDataMap, sendNotification };