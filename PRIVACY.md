# Privacy

ShareDroid has no servers, accounts, analytics, ads or crash reporting. The developer never receives your files, links or credentials.

## What the app stores on your device

- **Destination settings**, such as endpoint, bucket and key template.
- **Credentials** (access keys and secrets, and the headers of custom HTTP destinations, which often hold API tokens), encrypted with a key held in the Android Keystore. They are never stored in plain text.
- **Upload history**: file name, size, upload time and the resulting link. You can remove entries at any time.
- **A private copy of each file while it uploads**, so a failed upload can be retried. It is deleted as soon as the upload succeeds or you remove the entry.

## What the app sends, and where

When you share a file to ShareDroid, it uploads that file **only to the destination you configured**, such as your own S3 bucket or a URL you entered. That provider's privacy policy then applies to the file.

By default, ShareDroid removes location (GPS) and camera metadata from JPEG photos before uploading. Other file types, including videos and PNGs, are uploaded unchanged. You can turn this off in settings.

## Permissions

- **Internet**: to upload to your destination.
- **Notifications**: to show upload progress and the finished link.

ShareDroid only reads the files you explicitly share to it.
