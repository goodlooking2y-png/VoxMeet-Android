package com.rakib.voxmeet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

private val AuthBg = Color(0xFF100D20)
private val AuthPanelColor = Color(0xFF211A35)
private val AuthPinkColor = Color(0xFFFF4D91)
private val AuthVioletColor = Color(0xFF9167FF)
private val AuthMutedColor = Color(0xFFB9B2C9)

@Composable
fun VoxMeetAuthGate(content: @Composable () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    when {
        user == null -> VoxMeetAuthScreen(auth)
        user!!.isEmailVerified || !user!!.phoneNumber.isNullOrBlank() -> content()
        else -> EmailVerificationScreen(auth, user!!) {
            user!!.reload().addOnCompleteListener { user = auth.currentUser }
        }
    }
}

@Composable
private fun VoxMeetAuthScreen(auth: FirebaseAuth) {
    var mode by remember { mutableStateOf("email") }
    var register by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("আপনার VoxMeet অ্যাকাউন্ট তৈরি করুন অথবা লগইন করুন") }
    val context = LocalContext.current

    Surface(Modifier.fillMaxSize(), color = AuthBg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("VOXMEET", color = AuthPinkColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            Text("Connect, share and shine", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("নিরাপদে অ্যাকাউন্টে প্রবেশ করুন", color = AuthMutedColor, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().background(AuthPanelColor, RoundedCornerShape(14.dp)).padding(4.dp)) {
                listOf("email" to "ইমেইল", "phone" to "ফোন OTP").forEach { (key, label) ->
                    TextButton(onClick = { mode = key; message = if (key == "email") "ইমেইল দিয়ে প্রবেশ করুন" else "দেশের কোডসহ ফোন নম্বর দিন" }, modifier = Modifier.weight(1f)) {
                        Text(label, color = if (mode == key) Color.White else AuthMutedColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (mode == "email") {
                AuthInput(email, { email = it }, "ইমেইল ঠিকানা")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("পাসওয়ার্ড") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), colors = authInputColors()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (email.isBlank() || password.length < 6) {
                            message = "সঠিক ইমেইল দিন এবং কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড লিখুন"
                        } else {
                            busy = true
                            val task = if (register) auth.createUserWithEmailAndPassword(email.trim(), password)
                                else auth.signInWithEmailAndPassword(email.trim(), password)
                            task.addOnCompleteListener { result ->
                                if (!result.isSuccessful) {
                                    busy = false
                                    message = result.exception?.localizedMessage ?: "লগইন/রেজিস্ট্রেশন হয়নি"
                                } else {
                                    val current = auth.currentUser
                                    if (register && current != null) {
                                        current.sendEmailVerification().addOnCompleteListener { sent ->
                                            busy = false
                                            message = if (sent.isSuccessful) "ভেরিফিকেশন ইমেইল পাঠানো হয়েছে। Inbox/Spam দেখে লিংকে চাপ দিন।"
                                                else sent.exception?.localizedMessage ?: "ভেরিফিকেশন ইমেইল পাঠানো যায়নি"
                                        }
                                    } else {
                                        busy = false
                                        message = if (current?.isEmailVerified == true) "লগইন সফল" else "ইমেইল এখনো ভেরিফাই হয়নি। ইমেইল থেকে ভেরিফাই করুন।"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuthVioletColor)
                ) { Text(if (busy) "অপেক্ষা করুন..." else if (register) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন") }
                TextButton(onClick = { register = !register; message = if (register) "নতুন অ্যাকাউন্ট তৈরি করুন" else "আপনার অ্যাকাউন্টে লগইন করুন" }) {
                    Text(if (register) "আগে অ্যাকাউন্ট আছে? লগইন করুন" else "নতুন ব্যবহারকারী? রেজিস্ট্রেশন করুন", color = AuthPinkColor)
                }
                TextButton(onClick = {
                    if (email.isBlank()) message = "পাসওয়ার্ড রিসেটের জন্য আগে ইমেইল লিখুন"
                    else {
                        busy = true
                        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { result ->
                            busy = false
                            message = if (result.isSuccessful) "পাসওয়ার্ড রিসেটের ইমেইল পাঠানো হয়েছে"
                                else result.exception?.localizedMessage ?: "রিসেট ইমেইল পাঠানো যায়নি"
                        }
                    }
                }, enabled = !busy) { Text("পাসওয়ার্ড ভুলে গেছেন?", color = AuthMutedColor) }
            } else {
                AuthInput(phone, { phone = it }, "ফোন নম্বর (+880...)")
                Spacer(Modifier.height(10.dp))
                if (verificationId == null) {
                    Button(onClick = {
                        val activity = context.findHostActivity()
                        if (activity == null || !phone.trim().startsWith("+")) {
                            message = "দেশের কোডসহ নম্বর লিখুন, যেমন +8801XXXXXXXXX"
                        } else {
                            busy = true
                            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                                    auth.signInWithCredential(credential).addOnCompleteListener { result ->
                                        busy = false
                                        message = if (result.isSuccessful) "ফোন ভেরিফিকেশন সফল"
                                            else result.exception?.localizedMessage ?: "ফোন লগইন হয়নি"
                                    }
                                }
                                override fun onVerificationFailed(error: com.google.firebase.FirebaseException) {
                                    busy = false
                                    message = error.localizedMessage ?: "OTP পাঠানো যায়নি। নম্বর ও Firebase সেটিংস পরীক্ষা করুন।"
                                }
                                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                    verificationId = id
                                    busy = false
                                    message = "OTP পাঠানো হয়েছে। SMS থেকে কোড লিখুন।"
                                }
                            }
                            val options = PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone.trim())
                                .setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build()
                            PhoneAuthProvider.verifyPhoneNumber(options)
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AuthVioletColor)) {
                        Text(if (busy) "OTP পাঠানো হচ্ছে..." else "OTP পাঠান")
                    }
                } else {
                    AuthInput(otp, { otp = it }, "SMS-এর OTP কোড")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val id = verificationId
                        if (id.isNullOrBlank() || otp.isBlank()) message = "OTP কোড লিখুন"
                        else {
                            busy = true
                            auth.signInWithCredential(PhoneAuthProvider.getCredential(id, otp.trim())).addOnCompleteListener { result ->
                                busy = false
                                message = if (result.isSuccessful) "ফোন লগইন সফল"
                                    else result.exception?.localizedMessage ?: "OTP সঠিক নয়"
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AuthVioletColor)) { Text("OTP যাচাই করুন") }
                    TextButton(onClick = { verificationId = null; otp = "" }) { Text("নম্বর পরিবর্তন করুন", color = AuthPinkColor) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = AuthMutedColor, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("আপনার তথ্য Firebase Authentication দিয়ে যাচাই করা হবে।", color = AuthMutedColor, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmailVerificationScreen(auth: FirebaseAuth, user: FirebaseUser, onRefresh: () -> Unit) {
    var message by remember { mutableStateOf("আপনার ইমেইলে পাঠানো লিংকে চাপ দিয়ে অ্যাকাউন্ট ভেরিফাই করুন।") }
    var busy by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = AuthBg) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ইমেইল যাচাই করুন", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(user.email ?: "", color = AuthPinkColor)
            Spacer(Modifier.height(16.dp))
            Text(message, color = AuthMutedColor)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                busy = true
                user.reload().addOnCompleteListener {
                    busy = false
                    onRefresh()
                    message = if (auth.currentUser?.isEmailVerified == true) "ইমেইল ভেরিফাই হয়েছে" else "এখনো ভেরিফাই হয়নি। ইমেইলের লিংকে চাপ দিন।"
                }
            }, enabled = !busy) { Text("ভেরিফিকেশন চেক করুন") }
            TextButton(onClick = {
                busy = true
                user.sendEmailVerification().addOnCompleteListener { result ->
                    busy = false
                    message = if (result.isSuccessful) "আবার ভেরিফিকেশন ইমেইল পাঠানো হয়েছে"
                        else result.exception?.localizedMessage ?: "ইমেইল পাঠানো যায়নি"
                }
            }, enabled = !busy) { Text("আবার ইমেইল পাঠান", color = AuthPinkColor) }
            TextButton(onClick = { auth.signOut() }) { Text("অন্য অ্যাকাউন্ট দিয়ে প্রবেশ করুন", color = AuthMutedColor) }
        }
    }
}

@Composable
private fun AuthInput(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = authInputColors())
}

@Composable
private fun authInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = AuthVioletColor, unfocusedBorderColor = AuthPanelColor,
    focusedLabelColor = AuthPinkColor, unfocusedLabelColor = AuthMutedColor,
    cursorColor = AuthPinkColor, focusedContainerColor = AuthPanelColor, unfocusedContainerColor = AuthPanelColor
)

private fun Context.findHostActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
