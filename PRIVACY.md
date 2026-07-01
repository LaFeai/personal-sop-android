# Privacy

`个人sop` is a local Android app.

- The app stores user settings locally in Android SharedPreferences.
- The app does not provide accounts or login.
- The app does not provide cloud sync.
- The app does not upload module configuration to the project author.
- The Bark token is entered by the user and stored on the user's device.
- When a reminder is sent, the third-party 全能消息推送 Bark service receives the token, module title, and reminder message.
- Users should avoid writing sensitive personal information in reminder titles or messages.

The third-party push endpoint used by this app is:

```text
http://www.ggsuper.com.cn/push/api/v1/sendMsg_New.php
```
