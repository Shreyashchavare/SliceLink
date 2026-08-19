import React, { useState } from 'react';
import { Button } from '../common/Button';
import { useToast } from '../../context/ToastContext';

interface CopyUrlButtonProps {
  shortCode: string;
  size?: 'sm' | 'md';
  variant?: 'outline' | 'ghost' | 'secondary';
}

export const CopyUrlButton: React.FC<CopyUrlButtonProps> = ({
  shortCode,
  size = 'sm',
  variant = 'outline',
}) => {
  const [copied, setCopied] = useState<boolean>(false);
  const { showSuccess, showError } = useToast();

  const publicBaseUrl = import.meta.env.VITE_PUBLIC_URL || 'http://localhost:8080';
  const fullShortUrl = `${publicBaseUrl.replace(/\/$/, '')}/${shortCode}`;

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(fullShortUrl);
      } else {
        // Fallback for non-secure / local development
        const textArea = document.createElement('textarea');
        textArea.value = fullShortUrl;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        textArea.remove();
      }
      setCopied(true);
      showSuccess(`Short URL copied: ${fullShortUrl}`);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy URL:', err);
      showError('Failed to copy URL to clipboard');
    }
  };

  return (
    <Button
      type="button"
      size={size}
      variant={copied ? 'secondary' : variant}
      onClick={handleCopy}
      title={`Copy ${fullShortUrl}`}
      className="btn-copy"
    >
      {copied ? '✓ Copied' : '📋 Copy'}
    </Button>
  );
};

export default CopyUrlButton;
