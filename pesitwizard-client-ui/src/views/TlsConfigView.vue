<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { 
  Shield, 
  Key, 
  Lock, 
  Upload, 
  RefreshCw, 
  Trash2,
  CheckCircle,
  AlertCircle,
  Calendar,
  Info,
  FileKey
} from 'lucide-vue-next'
import api from '@/api'

interface TlsConfig {
  enabled: boolean
  keystorePath?: string
  keystoreConfigured: boolean
  truststorePath?: string
  truststoreConfigured: boolean
  clientCertSubject?: string
  clientCertExpiry?: string
  caCertSubject?: string
  caCertExpiry?: string
}

// State
const config = ref<TlsConfig>({
  enabled: false,
  keystoreConfigured: false,
  truststoreConfigured: false
})
const loading = ref(true)

// Modals
const showKeystoreModal = ref(false)
const showTruststoreModal = ref(false)

// Form data
const keystoreForm = ref({
  file: null as File | null,
  password: '',
  keyPassword: ''
})

const truststoreForm = ref({
  file: null as File | null,
  password: ''
})

// Actions state
const saving = ref(false)
const error = ref('')
const success = ref('')

onMounted(async () => {
  await loadConfig()
})

async function loadConfig() {
  loading.value = true
  try {
    const response = await api.get('/config/tls')
    config.value = response.data || {
      enabled: false,
      keystoreConfigured: false,
      truststoreConfigured: false
    }
  } catch (e) {
    console.debug('TLS config not available:', e)
  } finally {
    loading.value = false
  }
}

async function uploadKeystore() {
  if (!keystoreForm.value.file) {
    error.value = 'Please select a keystore file'
    return
  }

  saving.value = true
  error.value = ''
  try {
    const formData = new FormData()
    formData.append('file', keystoreForm.value.file)
    formData.append('password', keystoreForm.value.password)
    if (keystoreForm.value.keyPassword) {
      formData.append('keyPassword', keystoreForm.value.keyPassword)
    }

    await api.post('/config/tls/keystore', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    success.value = 'Keystore imported successfully'
    showKeystoreModal.value = false
    keystoreForm.value = { file: null, password: '', keyPassword: '' }
    await loadConfig()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to import keystore'
  } finally {
    saving.value = false
  }
}

async function uploadTruststore() {
  if (!truststoreForm.value.file) {
    error.value = 'Please select a truststore file'
    return
  }

  saving.value = true
  error.value = ''
  try {
    const formData = new FormData()
    formData.append('file', truststoreForm.value.file)
    formData.append('password', truststoreForm.value.password)

    await api.post('/config/tls/truststore', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    success.value = 'Truststore imported successfully (CA certificate)'
    showTruststoreModal.value = false
    truststoreForm.value = { file: null, password: '' }
    await loadConfig()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to import truststore'
  } finally {
    saving.value = false
  }
}

async function toggleTls() {
  try {
    await api.put('/config/tls/enabled', { enabled: !config.value.enabled })
    config.value.enabled = !config.value.enabled
    success.value = config.value.enabled ? 'TLS enabled' : 'TLS disabled'
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to toggle TLS'
  }
}

async function removeKeystore() {
  if (!confirm('Remove client keystore? You will need to import a new one for mTLS.')) return
  try {
    await api.delete('/config/tls/keystore')
    success.value = 'Keystore removed'
    await loadConfig()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to remove keystore'
  }
}

async function removeTruststore() {
  if (!confirm('Remove truststore? You may not be able to verify server certificates.')) return
  try {
    await api.delete('/config/tls/truststore')
    success.value = 'Truststore removed'
    await loadConfig()
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to remove truststore'
  }
}

function onKeystoreFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  keystoreForm.value.file = input.files?.[0] || null
}

function onTruststoreFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  truststoreForm.value.file = input.files?.[0] || null
}

function formatDate(dateStr?: string) {
  if (!dateStr) return 'N/A'
  return new Date(dateStr).toLocaleDateString('fr-FR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

function isExpiringSoon(dateStr?: string) {
  if (!dateStr) return false
  const expiry = new Date(dateStr)
  const thirtyDays = 30 * 24 * 60 * 60 * 1000
  return expiry.getTime() - Date.now() < thirtyDays
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-gray-900">TLS Configuration</h1>
      <button @click="loadConfig" class="btn btn-secondary flex items-center gap-2" :disabled="loading">
        <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        Refresh
      </button>
    </div>

    <!-- Info Banner -->
    <div class="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
      <div class="flex items-start gap-3">
        <Info class="h-5 w-5 text-blue-600 mt-0.5" />
        <div class="text-sm text-blue-700">
          <p class="font-medium mb-1">Secure Connection Setup</p>
          <p>To connect to a PeSIT server with mTLS (mutual TLS), you need:</p>
          <ul class="list-disc ml-4 mt-1 space-y-1">
            <li><strong>Truststore</strong> - Contains the CA certificate to verify the server</li>
            <li><strong>Keystore</strong> - Contains your client certificate (signed by the CA) for authentication</li>
          </ul>
          <p class="mt-2 text-blue-600">Contact your PeSIT server administrator to obtain these certificates.</p>
        </div>
      </div>
    </div>

    <!-- Success/Error Messages -->
    <div v-if="success" class="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm flex items-center gap-2">
      <CheckCircle class="h-4 w-4" />
      {{ success }}
    </div>
    <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm flex items-center gap-2">
      <AlertCircle class="h-4 w-4" />
      {{ error }}
    </div>

    <!-- TLS Status Card -->
    <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div :class="['p-3 rounded-lg', config.enabled ? 'bg-green-100' : 'bg-gray-100']">
            <Lock :class="['h-8 w-8', config.enabled ? 'text-green-600' : 'text-gray-400']" />
          </div>
          <div>
            <h2 class="text-lg font-semibold text-gray-900">TLS/mTLS Status</h2>
            <p :class="config.enabled ? 'text-green-600' : 'text-gray-500'">
              {{ config.enabled ? 'TLS is enabled for secure connections' : 'TLS is disabled (plain TCP)' }}
            </p>
          </div>
        </div>
        
        <label class="flex items-center gap-3 cursor-pointer">
          <span class="text-sm text-gray-600">{{ config.enabled ? 'Enabled' : 'Disabled' }}</span>
          <button 
            @click="toggleTls"
            :class="[
              'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
              config.enabled ? 'bg-green-600' : 'bg-gray-300'
            ]"
            :disabled="!config.keystoreConfigured || !config.truststoreConfigured"
          >
            <span
              :class="[
                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                config.enabled ? 'translate-x-6' : 'translate-x-1'
              ]"
            />
          </button>
        </label>
      </div>
      
      <div v-if="!config.keystoreConfigured || !config.truststoreConfigured" class="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg text-yellow-700 text-sm">
        <AlertCircle class="h-4 w-4 inline mr-1" />
        Configure both keystore and truststore to enable TLS
      </div>
    </div>

    <!-- Certificates Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Truststore (CA Certificate) -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-3">
            <div :class="['p-2 rounded-lg', config.truststoreConfigured ? 'bg-green-100' : 'bg-gray-100']">
              <Shield :class="['h-6 w-6', config.truststoreConfigured ? 'text-green-600' : 'text-gray-400']" />
            </div>
            <div>
              <h3 class="font-semibold text-gray-900">Truststore (CA Certificate)</h3>
              <p class="text-sm text-gray-500">To verify server identity</p>
            </div>
          </div>
        </div>

        <div v-if="config.truststoreConfigured" class="space-y-3">
          <div class="p-3 bg-gray-50 rounded-lg">
            <p class="text-sm text-gray-600">
              <strong>CA Subject:</strong> {{ config.caCertSubject || 'N/A' }}
            </p>
            <div v-if="config.caCertExpiry" class="flex items-center gap-1 mt-1">
              <Calendar class="h-3 w-3 text-gray-400" />
              <span :class="['text-xs', isExpiringSoon(config.caCertExpiry) ? 'text-red-600' : 'text-gray-500']">
                Expires: {{ formatDate(config.caCertExpiry) }}
              </span>
            </div>
          </div>
          
          <div class="flex gap-2">
            <button @click="showTruststoreModal = true" class="btn btn-secondary flex-1 flex items-center justify-center gap-2">
              <Upload class="h-4 w-4" />
              Replace
            </button>
            <button @click="removeTruststore" class="btn btn-secondary text-red-600 hover:bg-red-50">
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
        </div>

        <div v-else class="text-center py-6">
          <Shield class="h-12 w-12 mx-auto mb-3 text-gray-300" />
          <p class="text-gray-500 mb-4">No truststore configured</p>
          <button @click="showTruststoreModal = true" class="btn btn-primary flex items-center gap-2 mx-auto">
            <Upload class="h-4 w-4" />
            Import Truststore
          </button>
        </div>
      </div>

      <!-- Keystore (Client Certificate) -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-3">
            <div :class="['p-2 rounded-lg', config.keystoreConfigured ? 'bg-green-100' : 'bg-gray-100']">
              <Key :class="['h-6 w-6', config.keystoreConfigured ? 'text-green-600' : 'text-gray-400']" />
            </div>
            <div>
              <h3 class="font-semibold text-gray-900">Keystore (Client Certificate)</h3>
              <p class="text-sm text-gray-500">For mTLS authentication</p>
            </div>
          </div>
        </div>

        <div v-if="config.keystoreConfigured" class="space-y-3">
          <div class="p-3 bg-gray-50 rounded-lg">
            <p class="text-sm text-gray-600">
              <strong>Subject:</strong> {{ config.clientCertSubject || 'N/A' }}
            </p>
            <div v-if="config.clientCertExpiry" class="flex items-center gap-1 mt-1">
              <Calendar class="h-3 w-3 text-gray-400" />
              <span :class="['text-xs', isExpiringSoon(config.clientCertExpiry) ? 'text-red-600' : 'text-gray-500']">
                Expires: {{ formatDate(config.clientCertExpiry) }}
              </span>
            </div>
          </div>
          
          <div class="flex gap-2">
            <button @click="showKeystoreModal = true" class="btn btn-secondary flex-1 flex items-center justify-center gap-2">
              <Upload class="h-4 w-4" />
              Replace
            </button>
            <button @click="removeKeystore" class="btn btn-secondary text-red-600 hover:bg-red-50">
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
        </div>

        <div v-else class="text-center py-6">
          <Key class="h-12 w-12 mx-auto mb-3 text-gray-300" />
          <p class="text-gray-500 mb-4">No keystore configured</p>
          <button @click="showKeystoreModal = true" class="btn btn-primary flex items-center gap-2 mx-auto">
            <Upload class="h-4 w-4" />
            Import Keystore
          </button>
        </div>
      </div>
    </div>

    <!-- Help Section -->
    <div class="mt-6 card bg-gray-50">
      <h3 class="font-semibold text-gray-900 mb-3 flex items-center gap-2">
        <FileKey class="h-5 w-5" />
        How to obtain certificates
      </h3>
      <ol class="list-decimal ml-5 space-y-2 text-sm text-gray-600">
        <li>Contact your PeSIT server administrator</li>
        <li>Request a <strong>client certificate</strong> signed by the server's CA</li>
        <li>The administrator will provide:
          <ul class="list-disc ml-5 mt-1">
            <li><strong>Keystore</strong> (.p12 or .jks) - your client certificate + private key</li>
            <li><strong>CA Certificate</strong> (.pem or .crt) - to import into your truststore</li>
          </ul>
        </li>
        <li>Import both files using the buttons above</li>
      </ol>
    </div>

    <!-- Import Truststore Modal -->
    <div v-if="showTruststoreModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showTruststoreModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6">
          <h2 class="text-xl font-bold text-gray-900 mb-6 flex items-center gap-2">
            <Shield class="h-6 w-6 text-green-600" />
            Import Truststore (CA Certificate)
          </h2>

          <form @submit.prevent="uploadTruststore" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Truststore File *</label>
              <input 
                type="file" 
                accept=".p12,.pfx,.jks,.pem,.crt,.cer"
                @change="onTruststoreFileChange"
                class="input"
                required
              />
              <p class="text-xs text-gray-500 mt-1">Formats: PKCS12 (.p12, .pfx), JKS (.jks), or PEM (.pem, .crt)</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <input 
                v-model="truststoreForm.password" 
                type="password" 
                class="input"
                placeholder="Truststore password (if required)"
              />
            </div>

            <div class="flex justify-end gap-3 pt-4">
              <button type="button" @click="showTruststoreModal = false" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                {{ saving ? 'Importing...' : 'Import Truststore' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Import Keystore Modal -->
    <div v-if="showKeystoreModal" class="fixed inset-0 z-50 overflow-y-auto">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showKeystoreModal = false" />
        
        <div class="relative bg-white rounded-xl shadow-xl max-w-lg w-full p-6">
          <h2 class="text-xl font-bold text-gray-900 mb-6 flex items-center gap-2">
            <Key class="h-6 w-6 text-blue-600" />
            Import Keystore (Client Certificate)
          </h2>

          <form @submit.prevent="uploadKeystore" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Keystore File *</label>
              <input 
                type="file" 
                accept=".p12,.pfx,.jks"
                @change="onKeystoreFileChange"
                class="input"
                required
              />
              <p class="text-xs text-gray-500 mt-1">Formats: PKCS12 (.p12, .pfx) or JKS (.jks)</p>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Store Password *</label>
              <input 
                v-model="keystoreForm.password" 
                type="password" 
                class="input"
                required
                placeholder="Keystore password"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Key Password</label>
              <input 
                v-model="keystoreForm.keyPassword" 
                type="password" 
                class="input"
                placeholder="Leave empty if same as store password"
              />
            </div>

            <div class="flex justify-end gap-3 pt-4">
              <button type="button" @click="showKeystoreModal = false" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                {{ saving ? 'Importing...' : 'Import Keystore' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
