# SolarQuote – Solar Quotation Generator

Desktop app (Java Swing + embedded H2 database) for creating solar installation quotations,
saving them as PDF and printing them.

## Features

| Page | What it does |
|---|---|
| **Dashboard** | Quotes & value this month, accepted/conversion, pending follow-ups, 6-month chart, recent quotes |
| **New Quotation** | Customer + system details, product picker, **⚡ Auto-fill kit** (panels/inverter/structure/etc. sized to kW), editable line items, discount, live GST totals, amount in words, PM Surya Ghar subsidy estimate, Save / PDF / Print |
| **History** | Search by quote no / customer / phone, filter by status, open, duplicate, PDF, print, change status, delete |
| **Products** | Catalog with category, brand, unit, price, GST %, panel wattage, active flag |
| **Settings** | Company letterhead & logo, bank details, quote prefix & validity, default terms, PDF folder, subsidy rates, DB connection |

Quote numbers follow the Indian financial year: `SQ/2026-27/0001`.
Line items are copied into each quotation, so later catalog price changes never alter old quotes.

Light and dark themes: click **Dark mode** at the bottom of the sidebar (or `D`). The choice is remembered.

Shortcuts (Cmd on macOS, Ctrl elsewhere): `N` new quote · `S` save · `P` print · `H` history · `1` dashboard.

## Install (Windows)

Run `SolarQuote-1.0.0.exe`. It installs for the current user (no admin rights, no Java, no database
server needed) and adds a Start-menu and desktop shortcut. Open **Settings** and fill in your company
details before creating quotes.

- Data lives in `%LOCALAPPDATA%\SolarQuoteData` (kept when the app is uninstalled or upgraded).
- A backup zip is taken every time the app starts (last 10 kept, in `backups\`); **Settings → Data & Backup** has a "Back up now" button.
- To restore, close the app and replace the files in the data folder with the contents of a backup zip.
- Problems are logged to `solarquote.log` in the data folder.

## Build the Windows installer

The `.exe` must be built on Windows. Push a tag (`git tag v1.0.0 && git push --tags`) or run the
**Windows installer** workflow from the GitHub *Actions* tab; it runs the tests, builds the installer,
installs it silently and checks that it starts, then attaches `SolarQuote-1.0.0.exe` to the run.

## Develop

Needs Java 17+ and Maven. `mvn package` runs the tests and builds `target/solarquote.jar`;
`java -jar target/solarquote.jar` starts the app (data in `~/.solarquote` on macOS/Linux).

## Project layout

```
src/main/java/com/solarquote/
  Main.java                 startup: look & feel → DB connect → schema → main window
  db/        Db             embedded H2 database, schema creation / seed data, backups
  model/                    Product, Quotation, QuoteItem
  dao/                      ProductDao, QuotationDao, SettingsDao (plain JDBC)
  service/                  Pricing (GST, discount, subsidy), KitBuilder, Money (₹ formatting, words), Settings
  pdf/                      QuotePdf (OpenPDF), QuotePrinter (PDFBox → system print dialog)
  ui/                       MainFrame + Dashboard, Quotation, History, Products, Settings panels
```

## Notes

- Subsidy rates are editable under Settings → Subsidy; verify them against the current PM Surya Ghar scheme.
- The PDF shows GST split equally as CGST + SGST (intra-state supply).
