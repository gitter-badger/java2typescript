//
// STARTUP
//

var process = {
  argv:'',
  platform:'macosx',
  env: {
    TERM:'color'
  },
  stderr:undefined,
  stdout:undefined
}

load('./target/js/main.js');
