# 🚀 aiproxy - Your Easy AI API Solution

[![Download aiproxy](https://img.shields.io/badge/Download-aiproxy-brightgreen)](https://github.com/kreepthom/aiproxy/releases)

## 🌟 Overview

aiproxy is a powerful AI API proxy service tailored for businesses. It supports multiple AI providers such as Claude, ChatGPT, and Gemini. With a unified interface, user account management, and intelligent routing, it simplifies API interactions.

### ✨ Core Features

- **High Performance** - Built on a reactive architecture using Spring WebFlux, it supports efficient data streaming.
- **Account Pool** - Automatically switches between multiple accounts for balanced loads.
- **Secure Authentication** - Utilizes OAuth 2.0 with PKCE flow, JWT authentication, and API key management.
- **Monitoring Stats** - Tracks requests, usage statistics, and error logs.
- **Rate Limiting** - Manages request frequency and controls token usage.
- **Easy Deployment** - Containerized with Docker, allowing for simple environment setup.
- **Frontend Interface** - Admin dashboard built with React and TypeScript.

## 🛠️ Technical Stack

- **Backend**: Java 21, Spring Boot 3.2+, Spring WebFlux
- **Frontend**: React 18, TypeScript, Vite, TailwindCSS
- **Database**: MySQL 8.0+, Redis 7.0+
- **Deployment**: Docker, Docker Compose

## 🚀 Getting Started

### 🖥️ System Requirements

To run aiproxy, ensure you have the following:

- JDK 21 or higher
- Maven 3.8 or higher
- MySQL 8.0 or higher
- Redis 7.0 or higher
- Node.js 18 or higher (for frontend development)

### 📥 Download & Install

Visit this page to download: [Download aiproxy](https://github.com/kreepthom/aiproxy/releases)

### 📂 Installation Steps

1. **Clone the Repository**
   Open your terminal and run the following commands:
   ```bash
   git clone https://github.com/kreepthom/aiproxy.git
   cd aiproxy
   ```

2. **Configure Local Environment**
   Set up your local environment by modifying the configuration file:
   ```bash
   cd aiproxy-api/src/main/resources
   cp application-local.yml.example application-local.yml
   ```
   Edit `application-local.yml` with your database password and other settings.

3. **Start the Service**
   Launch the backend and frontend as follows:
   ```bash
   # Start Backend
   mvn spring-boot:run -pl aiproxy-api

   # Start Frontend (in a new terminal)
   cd frontend
   npm install
   npm run dev
   ```

4. **Access the Application**
   Open a web browser and visit `http://localhost:3000` to start using aiproxy.

## 🔍 Troubleshooting

If you encounter issues during installation or running the application, try the following:

- Ensure all required software is installed correctly.
- Check if the local environment configurations are set properly.
- Consult the logs for error messages for more details.

## 📄 Documentation

For more details on features, API usage, and advanced setup, refer to the [full documentation](https://github.com/kreepthom/aiproxy/docs).

## ✅ Support

If you need help, please open an issue on the GitHub page or contact our support team via email.

## 📞 Community

Join our community for support and to share your experiences:

- [Discord](https://discord.gg/example)
- [Twitter](https://twitter.com/example)

Thank you for choosing aiproxy! Enjoy your AI API integration journey.