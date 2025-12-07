package com.example.listit

object Constants {
    // CHANGE THIS IP ADDRESS HERE ONLY
    // Use "10.0.2.2" for Android Emulator
    // Use your PC's IP (e.g., "192.168.1.5") for Real Device
    private const val IP_ADDRESS = "192.168.100.11"

    const val BASE_URL = "https://lathiest-unmutualized-jeff.ngrok-free.dev/Listit/"
    const val LOGIN_URL = "${BASE_URL}login_user.php"
    const val REGISTER_URL = "${BASE_URL}register_user.php"
    const val EDIT_PROFILE_URL = "${BASE_URL}edit_profile.php"
}