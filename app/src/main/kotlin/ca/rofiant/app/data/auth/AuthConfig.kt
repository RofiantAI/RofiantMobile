package ca.rofiant.app.data.auth

// Same Supabase project rofiant-desktop talks to (src/lib/supabase.ts) —
// the mobile app is a second client against the same backend, not a fork.
object AuthConfig {
    const val SUPABASE_URL = "https://nxwzaztltnqdslnvehva.supabase.co"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im54d3phenRsdG5xZHNsbnZlaHZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIzNDY5NTIsImV4cCI6MjA5NzkyMjk1Mn0.ccB6uoDr3k7--Xm1QKeDo6sRE_82ZDwzimbcpHYiTjo"
    const val AUTH_BASE = "$SUPABASE_URL/auth/v1"
    const val FUNCTIONS_BASE = "$SUPABASE_URL/functions/v1"

    // Matches rofiant-desktop's rofiant:// deep-link scheme (src/lib/auth-redirect.ts)
    // registered as an intent-filter on MainActivity.
    const val AUTH_REDIRECT_URL = "rofiant://auth-callback"
    const val SIGNUP_URL = "https://rofiant.ca/en/auth/signup?client=mobile"
}
