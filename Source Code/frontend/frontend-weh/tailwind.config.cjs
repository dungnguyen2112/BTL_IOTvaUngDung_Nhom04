
/** @type {import('tailwindcss').Config} */
module.exports = {
  // Only scan our app files, NOT node_modules (fixes performance warning)
  content: [
    './index.html',
    './main.tsx',
    './smart_trash_dashboard.tsx',
  ],
  theme: {
    extend: {},
  },
  plugins: [],
};

