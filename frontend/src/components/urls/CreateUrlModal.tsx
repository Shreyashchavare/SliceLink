import React, { useState } from 'react';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { urlApi } from '../../api/urlApi';
import { NormalizedApiError, UrlResponse } from '../../api/types';
import { useToast } from '../../context/ToastContext';
import { validateUrl } from '../../utils/validationUtils';

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
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);
  const { showSuccess } = useToast();

  const handleClose = () => {
    if (loading) return;
    setOriginalUrl('');
    setFieldError(null);
    setApiError(null);
    onClose();
  };

  const handleUrlChange = (value: string) => {
    setOriginalUrl(value);
    if (fieldError) {
      setFieldError(null);
    }
  };

  const handleBlur = () => {
    if (originalUrl.trim()) {
      const err = validateUrl(originalUrl);
      setFieldError(err);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;

    setApiError(null);
    const trimmed = originalUrl.trim();
    const validationMessage = validateUrl(trimmed);

    if (validationMessage) {
      setFieldError(validationMessage);
      return;
    }

    setLoading(true);
    try {
      const created = await urlApi.createUrl({ originalUrl: trimmed });
      showSuccess(`Shortened link created: /${created.shortCode}`);
      onSuccess(created);
      handleClose();
    } catch (err) {
      const normErr = err as NormalizedApiError;
      if (normErr.status === 429) {
        setApiError({
          ...normErr,
          message: 'Rate limit exceeded. You are creating links too quickly. Please wait a moment.',
        });
      } else {
        setApiError(normErr);
      }
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
        <ErrorMessage error={apiError} />
        <Input
          label="Destination URL"
          type="url"
          placeholder="https://example.com/very/long/destination/url"
          value={originalUrl}
          onChange={(e) => handleUrlChange(e.target.value)}
          onBlur={handleBlur}
          error={fieldError}
          disabled={loading}
          required
          autoFocus
          helperText="Enter the full target URL including http:// or https://"
        />
      </form>
    </Modal>
  );
};

export default CreateUrlModal;
