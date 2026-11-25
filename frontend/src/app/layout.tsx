import type { Metadata, Viewport } from 'next';
import { AppProviders } from '@/providers/AppProviders';
import { Header } from '@/components/layout/Header';
import './globals.css';

export const metadata: Metadata = {
  title: { default: 'JobForge — Build what comes next', template: '%s · JobForge' },
  description: 'Discover better opportunities, build your professional network, and move your career forward with JobForge.',
  applicationName: 'JobForge',
  keywords: ['jobs', 'careers', 'recruitment', 'professional network', 'AI career'],
  openGraph: {
    title: 'JobForge — Build what comes next',
    description: 'A smarter place to discover opportunities and grow your career.',
    type: 'website',
  },
};
export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#f6f7fb' };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <AppProviders>
          <Header />
          <main>{children}</main>
          <footer className="border-t border-slate-200 bg-white">
            <div className="jf-container flex flex-col gap-3 py-7 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between">
              <span className="font-semibold tracking-tight text-slate-800">JobForge<span className="text-violet-600">.</span></span>
              <span>Find your next opportunity. Build what comes next.</span>
              <span>© {new Date().getFullYear()} JobForge</span>
            </div>
          </footer>
        </AppProviders>
      </body>
    </html>
  );
}
