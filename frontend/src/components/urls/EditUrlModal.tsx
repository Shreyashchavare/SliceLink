import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { urlApi } from '../../api/urlApi';
import { NormalizedApiError, UrlResponse } from '../../api/types';
import { useToast } from '../../context/ToastContext';
import { validateUrl } from '../../utils/validationUtils';

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
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [apiError, setApiError] = useState<NormalizedApiError | null>(null);
  const { showSuccess } = useToast();

  useEffect(() => {
    if (url) {
      setOriginalUrl(url.originalUrl);
      setFieldError(null);
      setApiError(null);
    }
  }, [url]);

  const handleClose = () => {
    if (loading) return;
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
    if (!url || loading) return;

    setApiError(null);
    const trimmed = originalUrl.trim();
    const validationMessage = validateUrl(trimmed);

    if (validationMessage) {
      setFieldError(validationMessage);
      return;
    }

    if (trimmed === url.originalUrl) {
      handleClose();
      return;
    }

    setLoading(true);
    try {
      const updated = await urlApi.updateUrl(url.id, { originalUrl: trimmed });
      showSuccess(`Short URL /${url.shortCode} destination updated.`);
      onSuccess(updated);
      handleClose();
    } catch (err) {
      const normErr = err as NormalizedApiError;
      if (normErr.status === 404) {
        setApiError({
          ...normErr,
          message: 'This URL could not be found or you do not have permission to edit it.',
        });
      } else if (normErr.status === 429) {
        setApiError({
          ...normErr,
          message: 'Rate limit exceeded. Please wait a moment before modifying links.',
        });
      } else {
        setApiError(normErr);
      }
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
        <ErrorMessage error={apiError} />
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
          onChange={(e) => handleUrlChange(e.target.value)}
          onBlur={handleBlur}
          error={fieldError}
          disabled={loading}
          required
          autoFocus
          helperText="Update the target redirection URL."
        />
      </form>
    </Modal>
  );
};

export default EditUrlModal;
