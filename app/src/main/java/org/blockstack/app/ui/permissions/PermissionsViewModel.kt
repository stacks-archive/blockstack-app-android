package org.blockstack.app.ui.permissions

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.blockstack.app.data.AuthRepository
import java.lang.Exception
import java.security.InvalidParameterException

class PermissionsViewModel(
    private val authRepository: AuthRepository,
    private val context: Context
) : ViewModel() {
    private val _scope = MutableLiveData<Scope>()
    val scope: LiveData<Scope> = _scope

    private val _grantedPermissions = MutableLiveData<List<String>>()
    val grantedPermissions:LiveData<List<String>> = _grantedPermissions

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

    fun load(scopeStrings: Array<String>, callingPackage: String?) {

        val packageName =
            try {
                context.packageManager.getPackageInfo(callingPackage, 0).applicationInfo.name
            } catch (e:Exception) {
                "unknown app"
            }

        scopes = scopeStrings.map { scope ->
            val descriptionResId = context.resources.getIdentifier(
                scope,
                "string",
                context.packageName
            )
            if (descriptionResId == 0) {
                throw InvalidParameterException("$scope not supported")
            } else {
                Scope(
                    scope,
                    context.getString(
                        descriptionResId, packageName
                    )
                )
            }
        }
        grantedPermissionList = arrayListOf()
        currentScopeIndex = 0
        _scope.value = scopes[0]
    }
}
