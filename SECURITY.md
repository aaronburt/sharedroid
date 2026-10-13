# Security policy

ShareDroid stores credentials for your storage accounts, so security reports are taken seriously.

## Reporting a vulnerability

Please **don't open a public issue**. Report it privately through GitHub: go to the **Security** tab of this repository and choose **Report a vulnerability**.

Include what you found, how to reproduce it, and the app version. You should get a reply within a week. Once a fix is released you'll be credited in the changelog unless you'd rather not be.

## Supported versions

Only the latest release gets security fixes.

## In scope

- Credentials leaking from the app's storage, logs, backups or crash output
- Uploads going anywhere other than the destination the user configured
- Exposing files the user did not choose to share
- Weaknesses in request signing (SigV4)
