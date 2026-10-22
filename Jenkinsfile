pipeline {
    agent any

    environment {
        // Credencial configurada en Jenkins (tipo "Secret text") con el Personal Access Token de GitHub
        GITHUB_TOKEN = credentials('GITHUB_TOKEN')
        REPO_OWNER   = 'Deathshot737'
        REPO_NAME    = 'NicTra'
        // Puedes ajustar o definir la ruta del Android SDK si tu agente Jenkins no lo tiene en el PATH global
        // ANDROID_HOME = '/opt/android-sdk'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '15'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Grant Permissions') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x ./gradlew'
                    }
                }
            }
        }

        stage('Build Debug APK') {
            steps {
                echo 'Compilando APK de pruebas (Debug)...'
                script {
                    if (isUnix()) {
                        sh '''
                            ./gradlew assembleDebug --no-daemon --stacktrace
                            cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/debug/NicTra.apk
                        '''
                    } else {
                        bat '''
                            gradlew.bat assembleDebug --no-daemon --stacktrace
                            copy /Y app\\build\\outputs\\apk\\debug\\app-debug.apk app\\build\\outputs\\apk\\debug\\NicTra.apk
                        '''
                    }
                }
            }
        }

        stage('Archive Artifacts') {
            steps {
                echo 'Guardando NicTra.apk en Jenkins...'
                archiveArtifacts artifacts: 'app/build/outputs/apk/debug/*.apk',
                        fingerprint: true,
                        allowEmptyArchive: false
            }
        }

        stage('Publish GitHub Pre-release') {
            steps {
                script {
                    def currentBranch = env.BRANCH_NAME ?: env.GIT_BRANCH ?: ''
                    echo "Rama detectada en Jenkins: '${currentBranch}'"

                    // Solo omitir si explícitamente estamos en una rama diferente a main
                    if (currentBranch && !currentBranch.endsWith('main')) {
                        echo "Omitiendo publicación de release porque no es la rama main (rama actual: ${currentBranch})."
                        return
                    }

                    def tag = "v1.0.0"
                    def releaseTitle = "Pre-release v1.0.0"
                    def releaseNotes = "Última versión de prueba compilada automáticamente desde la rama main (Build #${BUILD_NUMBER})."
                    def apkPath = "app/build/outputs/apk/debug/NicTra.apk"

                    echo "Gestionando Pre-release en GitHub: ${releaseTitle} con el archivo NicTra.apk..."

                    if (isUnix()) {
                        sh """
                            # 1. Intentar con GitHub CLI (gh) si está instalado
                            if command -v gh >/dev/null 2>&1; then
                                echo "Usando GitHub CLI (gh)..."
                                # Limpiar tag local si existiera desincronizado
                                git tag -d "${tag}" >/dev/null 2>&1 || true

                                if gh release view "${tag}" >/dev/null 2>&1; then
                                    echo "La release ${tag} ya existe. Actualizando NicTra.apk..."
                                    gh release upload "${tag}" "${apkPath}" --clobber
                                else
                                    echo "Creando nueva Pre-release ${tag}..."
                                    gh release create "${tag}" "${apkPath}" \
                                        --target main \
                                        --title "${releaseTitle}" \
                                        --notes "${releaseNotes}" \
                                        --prerelease
                                fi
                            else
                                echo "Usando GitHub REST API..."
                                
                                # Verificar si la release ya existe
                                GET_RESPONSE=\$(curl -s -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                                    -H "Accept: application/vnd.github+json" \
                                    https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/tags/${tag})

                                RELEASE_ID=\$(echo "\$GET_RESPONSE" | grep -m 1 -o '"id": [0-9]*' | awk '{print \$2}')

                                if [ -z "\$RELEASE_ID" ]; then
                                    echo "Creando nueva release ${tag}..."
                                    CREATE_RESPONSE=\$(curl -s -w "\\nHTTP_STATUS:%{http_code}" -X POST \
                                        -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                                        -H "Accept: application/vnd.github+json" \
                                        https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases \
                                        -d "{
                                            \\"tag_name\\": \\"${tag}\\",
                                            \\"target_commitish\\": \\"main\\",
                                            \\"name\\": \\"${releaseTitle}\\",
                                            \\"body\\": \\"${releaseNotes}\\",
                                            \\"draft\\": false,
                                            \\"prerelease\\": true
                                        }")

                                    HTTP_BODY=\$(echo "\$CREATE_RESPONSE" | sed -e '\$d')
                                    HTTP_STATUS=\$(echo "\$CREATE_RESPONSE" | tail -n 1 | cut -d':' -f2)

                                    if [ "\$HTTP_STATUS" -ne 201 ]; then
                                        echo "Error creando release (Código \$HTTP_STATUS): \$HTTP_BODY"
                                        exit 1
                                    fi

                                    RELEASE_ID=\$(echo "\$HTTP_BODY" | grep -m 1 -o '"id": [0-9]*' | awk '{print \$2}')
                                    echo "Release creada con ID: \$RELEASE_ID"
                                else
                                    echo "La release ya existe con ID: \$RELEASE_ID. Verificando si existe un asset previo..."
                                    
                                    # Buscar si ya existe NicTra.apk en los assets para eliminarlo y reemplazarlo
                                    OLD_ASSET_ID=\$(echo "\$GET_RESPONSE" | grep -B 2 -A 5 '"name": "NicTra.apk"' | grep -m 1 -o '"id": [0-9]*' | awk '{print \$2}')
                                    if [ -n "\$OLD_ASSET_ID" ]; then
                                        echo "Eliminando NicTra.apk anterior (Asset ID: \$OLD_ASSET_ID)..."
                                        curl -s -X DELETE \
                                            -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                                            -H "Accept: application/vnd.github+json" \
                                            "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/assets/\$OLD_ASSET_ID"
                                    fi
                                fi

                                echo "Subiendo nuevo NicTra.apk al release ID: \$RELEASE_ID..."
                                UPLOAD_RESPONSE=\$(curl -s -w "\\nHTTP_STATUS:%{http_code}" -X POST \
                                    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                                    -H "Content-Type: application/vnd.android.package-archive" \
                                    "https://uploads.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/\$RELEASE_ID/assets?name=NicTra.apk" \
                                    --data-binary "@${apkPath}")

                                UPLOAD_STATUS=\$(echo "\$UPLOAD_RESPONSE" | tail -n 1 | cut -d':' -f2)

                                if [ "\$UPLOAD_STATUS" -ne 201 ]; then
                                    echo "Error subiendo NicTra.apk (Código \$UPLOAD_STATUS):"
                                    echo "\$UPLOAD_RESPONSE"
                                    exit 1
                                fi

                                echo "NicTra.apk publicado exitosamente en la Pre-release!"
                            fi
                        """
                    } else {
                        bat """
                            gh release create "${tag}" "${apkPath}" --target main --title "${releaseTitle}" --notes "${releaseNotes}" --prerelease || gh release upload "${tag}" "${apkPath}" --clobber
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completado con éxito. APK generado y publicado como Pre-release."
        }
        failure {
            echo "El pipeline falló durante la ejecución. Revisa los logs de Gradle o de GitHub API."
        }
    }
}
