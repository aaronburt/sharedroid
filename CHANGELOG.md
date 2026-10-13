# Changelog

All notable changes to ShareDroid are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-10-13

First release. Android 12 or later.

### Added
- SigV4 request signing, tested against AWS's published examples.
- Object key templates with ShareX-style tokens such as `{yyyy}/{MM}/{random:8}.{ext}`.
- Home screen with upload history: copy, open, retry, remove, and delete from the bucket.
- Upload from the + button (photo picker, file picker or clipboard) and from any app's share sheet.
- Background uploads with progress, result and failure notifications. Android 14 and later use user-initiated data transfer jobs.
- S3 destinations: Amazon S3 or any S3-compatible provider, with live key and link previews and Test connection.
- Credentials encrypted with the Android Keystore.
- Public or expiring (presigned) links.
- Location and camera metadata removed from JPEG photos before upload.
- Custom HTTP destination: POST or PUT a file to any URL as a form or raw body, with headers, query parameters,
  extra fields, and a link and deletion link read from the reply (`$json:`, `$header:`, `$regex:`, `$response$`).
  The fields follow ShareX custom uploaders so `.sxcu` import can be added later.
- Upload service presets for Custom HTTP: Imgur (needs a free Client ID), Catbox and Litterbox. They fill in the
  form and can be edited like any custom destination.
- WebDAV destination (Nextcloud, ownCloud and others): PUT with Basic auth, folders created as needed, optional public
  URL base, delete through WebDAV.
- FTP destination: plain FTP, explicit FTPS and implicit FTPS, with a passive-mode switch, a required public URL base
  for links, and delete through FTP.
- App icon, and a release workflow that tests, builds and publishes a signed APK from a version tag.
- Appearance settings: accent colour presets or a custom colour, wallpaper colours, true black, custom Home title.

### Known untested

These work in unit tests and against local stand-in servers, but have not been tried against the real services:

- FTPS, both explicit and implicit. Servers with self-signed certificates are refused.
- Real Nextcloud, ownCloud and FTP servers (WebDAV and FTP were only checked against stand-ins).
- The Imgur, Catbox and Litterbox presets. Their request details come from the services' documentation and ShareX's
  configs. Use **Test upload** in the form to check one.
- Amazon S3 itself (Cloudflare R2 was tested for real).
- Large files, and JPEG location removal on a real camera photo.

### Known limits

- Release builds only allow `https://` addresses. Plain `http://` works in debug builds, for local testing.
- Imgur uploads cannot be deleted from the app: Imgur needs a DELETE request with a header, and deletion links are
  plain GET requests.
- Files over 5 GB are not supported yet (no multipart upload).

[Unreleased]: https://github.com/aaronburt/sharedroid/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/aaronburt/sharedroid/releases/tag/v0.1.0
