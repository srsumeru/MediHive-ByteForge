# Manipal Hackathon Round 1 submission checklist

Project: MediHive  
Team: ByteForge  
Problem Statement ID: PS02

## Presentation

- Use only the official Round 1 presentation template.
- Export and submit as PDF, not PPT or PPTX.
- Replace `TeamID` with the actual Team ID displayed in the portal.
- Required pattern: `ActualTeamID_ByteForge_PS02.pdf`.
- Do not include the institute name, institute logo, or college-identifying background elements.

## Video

- Use one English video covering the solution and prototype demonstration.
- Maximum duration is 3 minutes because this submission includes a prototype.
- Every registered team member must appear.
- Minimum resolution is 640 x 480.
- Host on YouTube as Unlisted/Public or Google Drive as Anyone with the link.
- Test the URL in an incognito browser before submitting.

## Prototype

- Keep this GitHub repository public and accessible throughout evaluation.
- Submit the exact public repository URL through the official registration portal.
- Verify that README.md, pom.xml, src/, docs/ and the Postman collection are visible.
- Do not publish real database passwords, API keys, tokens or personal information.

## Final technical check

Run from the repository root:

```bash
mvn test
mvn spring-boot:run
```

Then complete the five-account judge walkthrough in `docs/MediHive-User-Manual.pdf`.

## Portal submission package

- Presentation PDF: `ActualTeamID_ByteForge_PS02.pdf`
- Accessible video URL
- Public GitHub repository URL
- Any other mandatory text fields shown by the official portal

The official portal is the sole submission channel. Submit the final version before the deadline and verify the confirmation/status shown by the portal.
