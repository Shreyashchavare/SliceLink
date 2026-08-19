/**
 * Auth Events Bus for decoupled session management.
 * Decouples Axios interceptors from React context lifecycles, preventing circular dependencies.
 */

type AuthEventListener = () => void;

const unauthorizedListeners: Set<AuthEventListener> = new Set();

export const authEvents = {
  /**
   * Emits a 401 Unauthorized / session expired event.
   */
  emitUnauthorized(): void {
    unauthorizedListeners.forEach((listener) => {
      try {
        listener();
      } catch (err) {
        console.error('Error executing unauthorized listener:', err);
      }
    });
  },

  /**
   * Subscribes to 401 Unauthorized / session expired events.
   * Returns a cleanup unsubscribe callback.
   */
  onUnauthorized(listener: AuthEventListener): () => void {
    unauthorizedListeners.add(listener);
    return () => {
      unauthorizedListeners.delete(listener);
    };
  },
};

export default authEvents;
