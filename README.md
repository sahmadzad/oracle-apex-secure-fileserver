# 🌍 Green IT Architecture: Secure Tokenized Document Access for Oracle APEX

[![Oracle APEX](https://img.shields.io/badge/Oracle-APEX%2024.2-blue)](https://apex.oracle.com/)
[![Green IT](https://img.shields.io/badge/Strategy-Green%20IT-green)](#-why-this-matters)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> **"As a computer engineer, my goal is to automate society and protect nature for next generations. Moving to paperless is just the first step; how we build those systems matters."** — *Saeed Ahmadzad-Asl*

---

## 💡 Why This Architecture Matters?
Modern "paperless" systems often rely on storing massive **BLOBs** (Binary Large Objects) directly in the database. While convenient, this traditional approach leads to:
- 🔋 **High CPU & I/O usage** (massive energy consumption).
- 💾 **Tablespace Bloat** and slow RMAN backup windows.
- ⚡ **Inefficient Data Guard replication** of static files.

**This solution provides a "Green IT" alternative.** By offloading documents to a secure, tokenized file server, we reduce the database workload, shrink the carbon footprint, and maintain enterprise-grade security.

---

## 🚀 Key Benefits
- **Zero BLOBs in DB:** Keeps the database lean and high-performing.
- **Secure Tokenization:** Only authorized APEX sessions can access files via temporary, one-time tokens.
- **Scalable Infrastructure:** Offloads file delivery from the DB to optimized document servers.
- **Green Savings:** Lower hardware overhead directly translates to lower power consumption in data centers.

---

## 🛠 Project Components

This repository contains the full end-to-end implementation:

| File | Role |
| :--- | :--- |
| [**SaveDocumentV2.java**](./SaveDocumentV2.java) | Securely saves files to the Linux/Unix filesystem outside the DB. |
| [**AccessToDocumentV2.java**](./AccessToDocumentV2.java) | Generates secure access tokens and validates APEX session integrity. |
| [**ORDS_REST_EDU_session_validation.sql**](./ORDS_REST_EDU_session_validation_2025_12_29.sql) | The REST handler that verifies if an  is active and valid. |
| [**f128.sql**](./f128.sql) | Sample Oracle APEX Application showing the implementation logic. |

---

## ⚙️ How It Works (The Flow)
1. **Request:** A user in Oracle APEX clicks to view a document.
2. **Validation:** The Java REST layer calls the ORDS validation endpoint to check the user's current .
3. **Tokenization:** Upon successful validation, a unique secure path/token is provided.
4. **HTTPS Delivery:** The file is streamed directly from the server via HTTPS, bypassing the database processing engine completely.

---

## 🤝 Contributing & Recognition
This project is part of my contribution to the **Oracle ACE Program** and the global **Green IT** movement. 

**Author:** Saeed Ahmadzad-Asl  
**Contact:** [sahmadzad@gmail.com](mailto:sahmadzad@gmail.com)  

If this architecture helped your project, please give it a ⭐!

---

## 🏷️ Tags / Topics
#OracleAPEX #GreenIT #Sustainability #OracleDatabase #DatabaseArchitecture #LowCode #Java #ORDS #CleanCode #EnvironmentFriendly
