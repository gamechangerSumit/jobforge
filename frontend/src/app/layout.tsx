import type { Metadata } from 'next';
import { AppProviders } from '@/providers/AppProviders';
import { Header } from '@/components/layout/Header';
import './globals.css';
export const metadata: Metadata = { title: 'JobForge', description: 'AI-powered job marketplace and professional community' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body><AppProviders><Header /><main>{children}</main></AppProviders></body></html>; }
