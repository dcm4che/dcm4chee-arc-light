/// <reference lib="webworker" />
let clockInterval;
let count = 0;
addEventListener('message', ({ data }) => {
    if(data && data.serverTime){
      let currentServerTime = new Date(data.serverTime);
      clearInterval(clockInterval);
      clockInterval = setInterval(() => {
          currentServerTime.setMilliseconds(currentServerTime.getMilliseconds()+1000);
          if(count > 600){
              postMessage({
                  serverTime:currentServerTime,
                  refresh:true
              });
              count = 0;
          }else{
              postMessage({
                  serverTime:currentServerTime,
                  refresh:false
              });
              count++;
          }
      }, 1000);
    }
});
