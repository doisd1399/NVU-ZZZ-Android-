import fs from 'fs';

let content = fs.readFileSync('src/context/AppContext.tsx', 'utf8');

// Replace the block that creates toasts with nothing or just updating the ref
const blockToReplace = `                  .filter((notification) => !notification.lida && isVisible(notification))
                  .forEach((notification) => {
                    const popupKey = \`\${collectionName}:\${notification.id}\`;
                    if (shownNotificationPopupsRef.current.has(popupKey)) return;
                    shownNotificationPopupsRef.current.add(popupKey);

                    toast(notification.titulo, {
                      description: notification.mensagem,
                      duration: 8000,
                      closeButton: true,
                      action: {
                        label: "Ver",
                        onClick: () =>
                          window.dispatchEvent(
                            new CustomEvent("nvu-notification-open", {
                              detail: notification,
                            }),
                          ),
                      },
                    });
                  });`;

content = content.replace(blockToReplace, `                  // Notification toasts are now handled globally by NotificationToastListener
                  // We no longer trigger toasts directly here to prevent cache-race spam.`);

if (content.includes('Notification toasts are now handled globally')) {
  console.log('Successfully replaced toast block in AppContext.tsx');
  fs.writeFileSync('src/context/AppContext.tsx', content);
} else {
  console.log('Failed to find block in AppContext.tsx');
}
