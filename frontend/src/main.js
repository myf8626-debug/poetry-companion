import {createApp} from 'vue';
import {createRouter,createWebHistory} from 'vue-router';
import App from './App.vue';
const router=createRouter({history:createWebHistory(),routes:[{path:'/:pathMatch(.*)*',component:{template:'<span></span>'}}]});
createApp(App).use(router).mount('#app');
