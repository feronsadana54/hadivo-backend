enum AuthStatus { bootstrapping, unauthenticated, authenticated }

class AuthState {
  const AuthState._({
    required this.status,
    this.email,
    this.errorMessage,
    this.isSubmitting = false,
  });

  const AuthState.bootstrapping() : this._(status: AuthStatus.bootstrapping);

  const AuthState.unauthenticated({
    String? errorMessage,
    bool isSubmitting = false,
  }) : this._(
         status: AuthStatus.unauthenticated,
         errorMessage: errorMessage,
         isSubmitting: isSubmitting,
       );

  const AuthState.authenticated({required String email})
    : this._(status: AuthStatus.authenticated, email: email);

  final AuthStatus status;
  final String? email;
  final String? errorMessage;
  final bool isSubmitting;

  bool get isAuthenticated => status == AuthStatus.authenticated;
  bool get isBootstrapping => status == AuthStatus.bootstrapping;

  AuthState asSubmitting() {
    return AuthState._(
      status: status,
      email: email,
      errorMessage: null,
      isSubmitting: true,
    );
  }
}
