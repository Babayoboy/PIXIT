package com.example.pixit

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient

val supabase = createSupabaseClient(
    supabaseUrl = "https://ngvnucqhszkeqtaqwqwq.supabase.co",
    supabaseKey = "sb_publishable_-dJUas6oBNGxpVR-KGS3gQ_6aPdW9Ak"
) {
    install(Auth)
}