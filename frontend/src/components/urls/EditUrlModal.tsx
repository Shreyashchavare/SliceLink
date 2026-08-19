import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { urlApi } from '../../api/urlApi';
import { NormalizedApiError, UrlResponse } from '../../api/types';
import { useToast } from '../../context/ToastContext';

interface EditUrlModalProps {
  isOpen: boolean;
  url: UrlResponse | null;
  onClose: () => void;
  onSuccess: (url: UrlResponse) => void;
}

export const EditUrlModal: React.FC<EditUrlModalProps> = ({
  isOpen,
  url,
  onClose,
  onSuccess,
}) => {
  const [originalUrl, setOriginalUrl] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);
  const { showSuccess } = useToast();

  useEffect(() => {
    if (url) {
      setOriginalUrl(url.originalUrl);
      setError(null);
    }
  }, [url]);

  const handleClose = () => {
    setError(null);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url) return;

    const trimmed = originalUrl.trim();
    if (!trimmed) {
      setError({
        code: 'VALIDATION_ERROR',
        message: 'Destination URL is required.',
      });
      return;
    }

    if (!/^https?:\/\//i.test(trimmed)) {
      setError({
        code: 'INVALID_URL',
        message: 'Destination URL must start with http:// or https://',
      });
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const updated = await urlApi.updateUrl(url.id, { originalUrl: trimmed });
      showSuccess(`Short URL /${url.shortCode} updated successfully.`);
      onSuccess(updated);
      handleClose();
    } catch (err) {
      setError(err as NormalizedApiError);
    } finally {
      setLoading(false);
    }
  };

  if (!url) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={`Edit Destination — /${url.shortCode}`}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={loading}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="edit-url-form"
            variant="primary"
            isLoading={loading}
          >
            Save Changes
          </Button>
        </div>
      }
    >
      <form id="edit-url-form" onSubmit={handleSubmit}>
        <ErrorMessage error={error} />
        <Input
          label="Short Code"
          type="text"
          value={url.shortCode}
          disabled
          helperText="Short codes are immutable."
        />
        <Input
          label="Destination URL"
          type="url"
          value={originalUrl}
          onChange={(e) => setOriginalUrl(e.target.value)}
          required
          autoFocus
          helperText="Update the target redirection URL."
        />
      </form>
    </Modal>
  );
};

export default EditUrlModal;
