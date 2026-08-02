/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        navy: {
          50: '#E8EEF6',
          100: '#C9D6E8',
          200: '#9BB2D1',
          300: '#6D8EBA',
          400: '#3F6AA3',
          500: '#1A4576',
          600: '#0B2447',
          700: '#081B36',
          800: '#06152B',
          900: '#040F22',
        },
        canvas: '#F7F9FC',
        accent: {
          50: '#E6F5F3',
          100: '#C2E8E3',
          200: '#85D1C8',
          300: '#48BAAE',
          400: '#1FAE9E',
          500: '#0F9D8E',
          600: '#0C7E72',
          700: '#095F57',
          800: '#06403B',
          900: '#03201E',
        },
        status: {
          ontime: '#1B9C6E',
          delayed: '#E8871E',
          cancelled: '#D93636',
          waitlist: '#D93636',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        '12': ['12px', { lineHeight: '18px' }],
        '14': ['14px', { lineHeight: '21px' }],
        '16': ['16px', { lineHeight: '24px' }],
        '20': ['20px', { lineHeight: '28px' }],
        '24': ['24px', { lineHeight: '32px' }],
        '32': ['32px', { lineHeight: '40px' }],
        '40': ['40px', { lineHeight: '48px' }],
      },
      spacing: {
        '1': '4px',
        '2': '8px',
        '3': '12px',
        '4': '16px',
        '5': '20px',
        '6': '24px',
        '8': '32px',
        '10': '40px',
        '12': '48px',
        '16': '64px',
      },
      borderRadius: {
        card: '12px',
        btn: '8px',
      },
      boxShadow: {
        card: '0 1px 3px rgba(11, 36, 71, 0.06), 0 1px 2px rgba(11, 36, 71, 0.04)',
        hover: '0 8px 24px rgba(11, 36, 71, 0.12), 0 2px 6px rgba(11, 36, 71, 0.06)',
      },
    },
  },
  plugins: [],
};
