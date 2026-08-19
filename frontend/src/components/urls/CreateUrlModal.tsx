import React, { useState } from 'react';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { urlApi } from '../../api/urlApi';
import { NormalizedApiError, UrlResponse } from '../../api/types';
import { useToast } from '../../context/ToastContext';

interface CreateUrlModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (url: UrlResponse) => void;
}

export const CreateUrlModal: React.FC<CreateUrlModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [originalUrl, setOriginalUrl] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);
  const { showSuccess } = useToast();

  const handleClose = () => {
    setOriginalUrl('');
    setError(null);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const trimmed = originalUrl.trim();
    if (!trimmed) {
      setError({
        code: 'VALIDATION_ERROR',
        message: 'Destination URL is required.',
      });
      return;
    }

    // Basic URL protocol validation
    if (!/^https?:\/\//i.test(trimmed)) {
      setError({
        code: 'INVALID_URL',
        message: 'Destination URL must start with http:// or https://',
      });
      return;
    }

    setLoading(true);
    try {
      const created = await urlApi.createUrl({ originalUrl: trimmed });
      showSuccess(`Shortened link created: /${created.shortCode}`);
      onSuccess(created);
      handleClose();
    } catch (err) {
      setError(err as NormalizedApiError);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Shorten a New URL"
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
          <Button type="button" variant="secondary" onClick={handleClose} disabled={loading}>
            Cancel
          </Button>
          <Button
            type="submit"
            form="create-url-form"
            variant="primary"
            isLoading={loading}
          >
            Create Short Link
          </Button>
        </div>
      }
    >
      <form id="create-url-form" onSubmit={handleSubmit}>
        <ErrorMessage error={error} />
        <Input
          label="Destination URL"
          type="url"
          placeholder="https://example.com/very/long/destination/url"
          value={originalUrl}
          onChange={(e) => setOriginalUrl(e.target.value)}
          required
          autoFocus
          helperText="Enter the full target URL including http:// or https://"
        />
      </form>
    </Modal>
  );
};

export default CreateUrlModal;
