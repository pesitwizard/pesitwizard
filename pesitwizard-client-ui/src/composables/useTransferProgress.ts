import { Client, type IMessage } from '@stomp/stompjs'
import { onUnmounted, ref } from 'vue'

export interface TransferProgress {
  transferId: string
  bytesTransferred: number
  fileSize: number
  percentage: number
  lastSyncPoint: number
  status: string
  errorMessage?: string
  bytesTransferredFormatted?: string
  fileSizeFormatted?: string
}

export function useTransferProgress() {
  const progress = ref<TransferProgress | null>(null)
  const connected = ref(false)
  const error = ref<string | null>(null)
  
  let stompClient: Client | null = null
  let subscription: { unsubscribe: () => void } | null = null
  let pendingTransferId: string | null = null

  const connect = () => {
    if (stompClient?.connected) {
      console.log('[WS] Already connected')
      return
    }

    // Use native WebSocket with SockJS fallback URL format
    // Spring's SockJS endpoint supports raw WebSocket at /ws/websocket
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/websocket`
    
    console.log('[WS] Connecting to:', wsUrl)
    
    stompClient = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        console.log('[STOMP]', str)
      },
      onConnect: () => {
        connected.value = true
        error.value = null
        console.log('[WS] Connected!')
        // If there's a pending subscription, do it now
        if (pendingTransferId) {
          doSubscribe(pendingTransferId)
          pendingTransferId = null
        }
      },
      onDisconnect: () => {
        connected.value = false
        console.log('[WS] Disconnected')
      },
      onStompError: (frame) => {
        error.value = frame.headers['message'] || 'WebSocket error'
        console.error('[WS] STOMP error:', frame)
      },
      onWebSocketError: (event) => {
        console.error('[WS] WebSocket error:', event)
        error.value = 'WebSocket connection error'
      },
      onWebSocketClose: (event) => {
        console.log('[WS] WebSocket closed:', event.code, event.reason)
      }
    })

    stompClient.activate()
  }

  const subscribeToTransfer = (transferId: string) => {
    console.log('[WS] subscribeToTransfer called for:', transferId)
    
    if (!stompClient) {
      pendingTransferId = transferId
      connect()
      return
    }
    
    if (!stompClient.connected) {
      console.log('[WS] Not connected yet, will subscribe when connected')
      pendingTransferId = transferId
      return
    }
    
    doSubscribe(transferId)
  }

  const doSubscribe = (transferId: string) => {
    if (subscription) {
      subscription.unsubscribe()
    }

    const destination = `/topic/transfer/${transferId}/progress`
    console.log('[WS] Subscribing to:', destination)
    
    subscription = stompClient?.subscribe(destination, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body) as TransferProgress
        progress.value = data
        console.log('[WS] Progress:', data.bytesTransferredFormatted, '/', data.fileSizeFormatted, 
          data.percentage >= 0 ? `(${data.percentage}%)` : '')
      } catch (e) {
        console.error('[WS] Failed to parse progress message:', e)
      }
    }) || null
  }

  const unsubscribe = () => {
    if (subscription) {
      subscription.unsubscribe()
      subscription = null
    }
    pendingTransferId = null
  }

  const disconnect = () => {
    unsubscribe()
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
  }

  const reset = () => {
    progress.value = null
    error.value = null
    pendingTransferId = null
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    progress,
    connected,
    error,
    connect,
    subscribeToTransfer,
    unsubscribe,
    disconnect,
    reset
  }
}
