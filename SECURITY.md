# Security

HARM is designed for trusted local networks. Do not expose port `8765` to the internet.

- NVR credentials and the control token are entered locally and are not part of this repository.
- Android application backup is disabled to reduce accidental credential extraction.
- Keep the tablet and Home Assistant on a trusted, isolated IoT network when possible.
- Give the tablet a DHCP reservation and restrict access to port `8765` to the Home Assistant host.
- Do not commit real IP addresses, credentials, tokens, APKs, keystores, or Home Assistant configuration files.

Report security issues privately to the repository owner rather than opening a public issue containing credentials.
