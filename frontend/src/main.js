import { createApp } from 'vue'
import { ElDialog } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-message.css'
import App from './App.vue'
import { createAppRouter } from './router.js'
import './styles.css'

const app = createApp(App)
app.use(createAppRouter())
app.component('el-dialog', ElDialog)
app.mount('#app')
