package com.jarvis.assistant.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(
    val name: String,
    val phoneNumber: String,
    val contactId: Long = 0
)

sealed class ContactResolution {
    data class Found(val match: ContactMatch) : ContactResolution()
    data class Ambiguous(val matches: List<ContactMatch>) : ContactResolution()
    data object NotFound : ContactResolution()
    data object PermissionRequired : ContactResolution()
}

object ContactResolver {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun resolveContact(context: Context, query: String): ContactResolution {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return ContactResolution.NotFound

        // If direct phone number (only digits, spaces, dashes, +)
        if (cleanQuery.matches(Regex("^[+]?[0-9\\s\\-]{7,15}\$"))) {
            return ContactResolution.Found(
                ContactMatch(
                    name = cleanQuery,
                    phoneNumber = cleanQuery.replace(Regex("[\\s\\-]"), "")
                )
            )
        }

        if (!hasContactsPermission(context)) {
            return ContactResolution.PermissionRequired
        }

        val matches = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                while (it.moveToNext()) {
                    val name = it.getString(nameCol) ?: ""
                    val number = it.getString(numberCol) ?: ""
                    val contactId = if (idCol >= 0) it.getLong(idCol) else 0L

                    // Normalize phone number
                    val normalizedNumber = number.replace(Regex("[\\s\\-]"), "")
                    if (normalizedNumber.isNotBlank()) {
                        // Avoid duplicates with same name and number
                        if (matches.none { m -> m.name.equals(name, ignoreCase = true) && m.phoneNumber == normalizedNumber }) {
                            matches.add(ContactMatch(name, normalizedNumber, contactId))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return ContactResolution.NotFound
        }

        return when {
            matches.isEmpty() -> ContactResolution.NotFound
            matches.size == 1 -> ContactResolution.Found(matches.first())
            else -> {
                // Check if one is exact name match
                val exactMatches = matches.filter { it.name.equals(cleanQuery, ignoreCase = true) }
                if (exactMatches.size == 1) {
                    ContactResolution.Found(exactMatches.first())
                } else if (exactMatches.size > 1) {
                    ContactResolution.Ambiguous(exactMatches)
                } else {
                    ContactResolution.Ambiguous(matches.take(3))
                }
            }
        }
    }
}
