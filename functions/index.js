const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp(functions.config().firebase);

// If there's an update in a thread, send a notification to all involved users
exports.sendNotifications = functions.database.ref('/threads/{threadId}').onUpdate(event => {
  const snapshot = event.data;
  const threadId = event.params.threadId;

  // Notification details
  const threadTitle = snapshot.val().title;

  // Receives list of all users participating in thread
  const userIdsInThread = new Set(snapshot.val().users);

  const payload = {
    notification: {
      title: `New Activity in a Question!`,
      body: threadTitle ? (threadTitle.length <= 100 ? threadTitle : threadTitle.substring(0, 97) + '...') : '',
      click_action: 'android.intent.action.MAIN'
    }
  };

  // Get the list of device tokens corresponding to the involved users
  return admin.database().ref('fcm_tokens').once('value').then(allTokens => {
    if (allTokens.val()) {
      // Listing all tokens
      const tokens = Object.keys(allTokens.val());
      let tokensToSend = [];

      // Add tokens that correspond to the list of users
      Object.keys(allTokens.val()).forEach(function(token) {
      	let fcmUserId = allTokens.val()[token];
      	if (userIdsInThread.has(fcmUserId)) {
      		tokensToSend.push(token);
      	}
      });

      console.log("Sending notifications to: ", tokensToSend);

      // Send notifications to all tokens
      return admin.messaging().sendToDevice(tokensToSend, payload).then(response => {
        // For each message check if there was an error.
        const tokensToRemove = [];
        response.results.forEach((result, index) => {
          const error = result.error;
          if (error) {
            console.error('Failure sending notification to', tokens[index], error);
            // Cleanup the tokens who are not registered anymore.
            if (error.code === 'messaging/invalid-registration-token' ||
                error.code === 'messaging/registration-token-not-registered') {
              tokensToRemove.push(allTokens.ref.child(tokens[index]).remove());
            }
          }
        });
        return Promise.all(tokensToRemove);
      });
    }
  });
});