import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/attendance/presentation/history_screen.dart';
import '../../features/attendance/presentation/home_screen.dart';
import '../../features/auth/domain/auth_state.dart';
import '../../features/auth/presentation/auth_controller.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/leave_request/presentation/leave_request_create_screen.dart';
import '../../features/leave_request/presentation/leave_request_list_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';
import '../../shared/widgets/app_shell.dart';
import '../../shared/widgets/splash_screen.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  final routerNotifier = _RouterNotifier(ref);
  ref.onDispose(routerNotifier.dispose);

  return GoRouter(
    initialLocation: '/home',
    navigatorKey: _rootNavigatorKey,
    refreshListenable: routerNotifier,
    redirect: routerNotifier.redirect,
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      ShellRoute(
        builder: (context, state, child) => AppShell(child: child),
        routes: [
          GoRoute(
            path: '/home',
            builder: (context, state) => const HomeScreen(),
          ),
          GoRoute(
            path: '/history',
            builder: (context, state) => const HistoryScreen(),
          ),
          GoRoute(
            path: '/requests',
            builder: (context, state) => const LeaveRequestListScreen(),
            routes: [
              GoRoute(
                path: 'new',
                parentNavigatorKey: _rootNavigatorKey,
                builder: (context, state) => const LeaveRequestCreateScreen(),
              ),
            ],
          ),
          GoRoute(
            path: '/profile',
            builder: (context, state) => const ProfileScreen(),
          ),
        ],
      ),
    ],
  );
});

final _rootNavigatorKey = GlobalKey<NavigatorState>();

class _RouterNotifier extends ChangeNotifier {
  _RouterNotifier(this._ref) {
    _subscription = _ref.listen<AuthState>(
      authControllerProvider,
      (previous, next) => notifyListeners(),
    );
  }

  final Ref _ref;
  late final ProviderSubscription<AuthState> _subscription;

  String? redirect(BuildContext context, GoRouterState state) {
    final authState = _ref.read(authControllerProvider);
    final location = state.uri.path;
    final isLogin = location == '/login';
    final isSplash = location == '/splash';

    if (authState.isBootstrapping) {
      return isSplash ? null : '/splash';
    }

    if (!authState.isAuthenticated) {
      return isLogin ? null : '/login';
    }

    if (isLogin || isSplash) return '/home';
    return null;
  }

  @override
  void dispose() {
    _subscription.close();
    super.dispose();
  }
}
