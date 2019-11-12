package org.blockstack.app.ui.permissions

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.blockstack.android.sdk.BaseScope.StoreWrite
import org.blockstack.app.R
import org.blockstack.app.data.AuthRepository
import java.security.InvalidParameterException

val COLLECTION_SCOPE_PREFIX = "collection."

class PermissionsViewModel(
    private val authRepository: AuthRepository,
    private val context: Context
) : ViewModel() {
    private val _scope = MutableLiveData<Scope>()
    val scope: LiveData<Scope> = _scope

    private val _grantedPermissions = MutableLiveData<ArrayList<String>>()
    val grantedPermissions: LiveData<ArrayList<String>> = _grantedPermissions

    private lateinit var scopes: List<Scope>
    private var grantedPermissionList: ArrayList<String> = arrayListOf()
    private var currentScopeIndex = 0

    fun deny() {
        handleNextScope()
    }

    fun allow() {
        grantedPermissionList.add(scopes[currentScopeIndex].name)
        handleNextScope()
    }

    private fun handleNextScope() {
        currentScopeIndex++
        if (currentScopeIndex >= scopes.size) {
            authRepository.grantPermissions(grantedPermissionList)
            _grantedPermissions.value = grantedPermissionList
        } else {
            _scope.value = scopes[currentScopeIndex]
        }
    }

    fun load(scopeStrings: Array<String>, callingPackage: String?, domain: String) {

        val packageName =
            if (callingPackage != null) {
                try {
                    context.packageManager.getPackageInfo(callingPackage, 0).applicationInfo.name
                } catch (e: Exception) {
                    "unknown app"
                }
            } else {
                domain.let {
                    if (domain.startsWith("https://")) {
                        domain.substring(8)
                    } else {
                        domain
                    }
                }
            }

        scopes = scopeStrings.map { scope ->
            val descriptionResId = context.resources.getIdentifier(
                scope,
                "string",
                context.packageName
            )
            if (descriptionResId == 0) {
                if (scope.startsWith(COLLECTION_SCOPE_PREFIX)) {
                 Scope(scope, scope.substring(COLLECTION_SCOPE_PREFIX.length))
                } else {
                    throw InvalidParameterException("$scope not supported")
                }
            } else {
                Scope(
                    scope,
                    context.getString(
                        descriptionResId, packageName
                    )
                )
            }
        }
        if (scopeStrings.indexOf(StoreWrite.scope.name) < 0) {
            scopes = listOf(Scope(
                StoreWrite.scope.name, context.getString(
                R.string.store_write, packageName)))
        }
        grantedPermissionList = arrayListOf()
        currentScopeIndex = 0

        if (scopes.isNotEmpty()) {
            _scope.value = scopes[0]
        } else {
            _grantedPermissions.value = grantedPermissionList
        }
    }
}
