import React, { ButtonHTMLAttributes, ReactNode } from 'react';
import { LoadingSpinner } from './LoadingSpinner';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
  children: ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  isLoading = false,
  leftIcon,
  rightIcon,
  children,
  disabled,
  className = '',
  ...props
}) => {
  return (
    <button
      className={`btn btn-${variant} btn-${size} ${isLoading ? 'btn-loading' : ''} ${className}`}
      disabled={disabled || isLoading}
      aria-busy={isLoading ? 'true' : undefined}
      aria-disabled={disabled || isLoading ? 'true' : undefined}
      {...props}
    >
      {isLoading ? (
        <LoadingSpinner size="sm" label="" />
      ) : (
        <>
          {leftIcon && <span className="btn-icon-left" aria-hidden="true">{leftIcon}</span>}
          <span>{children}</span>
          {rightIcon && <span className="btn-icon-right" aria-hidden="true">{rightIcon}</span>}
        </>
      )}
    </button>
  );
};

export default Button;
