# SolarQuote – Solar Quotation Generator

Desktop app (Java Swing + MySQL) for creating solar installation quotations,
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

Shortcuts (Cmd on macOS, Ctrl elsewhere): `N` new quote · `S` save · `P` print · `H` history · `1` dashboard.

## Requirements

- Java 17+
- MySQL 8+ running locally (or reachable on the network)

## Build & run

```bash
mvn package
java -jar target/solarquote.jar
```

On first launch the app asks for MySQL host, port, user and password.
The `solarquote` database and its tables are created automatically, with a starter product catalog.
Connection details are saved in `~/.solarquote/db.properties`.

Then open **Settings** and fill in your company details before creating quotes.

## Project layout

```
src/main/java/com/solarquote/
  Main.java                 startup: look & feel → DB connect → schema → main window
  db/        Db, DbConfig   JDBC connection + schema creation / seed data
  model/                    Product, Quotation, QuoteItem
  dao/                      ProductDao, QuotationDao, SettingsDao (plain JDBC)
  service/                  Pricing (GST, discount, subsidy), KitBuilder, Money (₹ formatting, words), Settings
  pdf/                      QuotePdf (OpenPDF), QuotePrinter (PDFBox → system print dialog)
  ui/                       MainFrame + Dashboard, Quotation, History, Products, Settings panels
```

## Notes

- Subsidy rates are editable under Settings → Subsidy; verify them against the current PM Surya Ghar scheme.
- The PDF shows GST split equally as CGST + SGST (intra-state supply).
