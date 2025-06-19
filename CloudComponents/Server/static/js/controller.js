const API_KEY = '';

const socket = io({
  extraHeaders: {
    'X-API-KEY': API_KEY
  }
});


// DATA RECEIVERS
socket.on('people_detected', function(data){
    document.getElementById("detectionResult").innerText =
        "People detected: " + data.count;

    drawBoxes(data);
});

socket.on('image_update', function(data){
    const selectedDeviceId = document.getElementById('deviceSelector').value;

    if (data.device_id === selectedDeviceId) {
        document.getElementById('selected-device-id').innerText = `Device: ${data.device_id}`;
        const img = document.getElementById('esp32-image');
        img.onload = resizeCanvas;
        img.src = 'data:image/jpeg;base64,' + data.image;
    }
});

socket.on('update', (msg) => {
    document.getElementById('esp-message').innerText = msg;
});

socket.on('distance_update', function(data) {
    const selectedDeviceId = document.getElementById('deviceSelector').value;

    if (data.device_id === selectedDeviceId) {
        document.getElementById('esp-distance').innerText = "Distance: " + data.distance;
    }
});

//OVERLAY DRAWER FUNCTIONS
function resizeCanvas(){
    const img = document.getElementById('esp32-image');
    const canvas = document.getElementById('overlay');
    canvas.width  = img.clientWidth;
    canvas.height = img.clientHeight;
}

function drawBoxes(data){
    const canvas = document.getElementById('overlay');
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (!data.boxes || !data.w) return;

    const scaleX = canvas.width / data.w;
    const scaleY = canvas.height / data.h;

    ctx.lineWidth = 2;
    ctx.strokeStyle = 'lime';

    data.boxes.forEach(b => {
        const [x1, y1, x2, y2] = b;
        ctx.strokeRect(x1 * scaleX, y1 * scaleY, (x2 - x1) * scaleX, (y2 - y1) * scaleY);
    });
}

//CONTROLS
function sendCommand(cmd) {
    const deviceId = document.getElementById('deviceSelector').value;

    const data = {
        device_id: deviceId,
        message: cmd
    };

    // EMIT TO SERVER
    socket.emit('esp32_command', data);
}

socket.on('lane_assist', function(data) {
        if (data && data.direction) {
            console.log("Lane Assist Direction:", data.direction);
            sendCommand(data.direction);
        }
    });

function updateDeviceIdInput() {
    const selectedDeviceId = document.getElementById('deviceSelector').value;
    document.getElementById('formDeviceId').value = selectedDeviceId;
}

document.getElementById('sendForm').addEventListener('submit', function(e) {
    e.preventDefault()

    updateDeviceIdInput();

    const message = document.getElementById('messageInput').value;
    const deviceId = document.getElementById('formDeviceId').value;

    const data = {
        device_id: deviceId,
        message: message
    };

    // EMIT TO SERVER
    socket.emit('esp32_command', data, (response) => {
        if (!(response && response.status === 'ok')) {
            console.error('Failed to send message');
        }
        document.getElementById('messageInput').value = ''
    });
});

socket.on('terminal_log', function(data) {
    const terminal = document.getElementById('terminal-output');
    const deviceId = data.device_id || "Unknown";
    const msg = data.message.replace(/\n/g, '<br>');
    const line = document.createElement('div');
    line.innerHTML = `[${deviceId}] ${msg}`;
    terminal.appendChild(line);

    terminal.scrollTop = terminal.scrollHeight;
});

socket.on('terminal_clear', function() {
  const terminal = document.getElementById('terminal-output');
  terminal.innerHTML = '';

  terminal.scrollTop = terminal.scrollHeight;
});


//COMMAND LIST FUNCTIONS

let isExecuting = false;
let currentTimeout = null;

function addCommand() {
    const container = document.createElement('div');
    container.className = 'command-item';
    container.style.marginBottom = '5px';

    const select = document.createElement('select');
    ['FORWARD', 'BACKWARD', 'LEFT', 'RIGHT'].forEach(dir => {
        const option = document.createElement('option');
        option.value = dir;
        option.textContent = dir;
        select.appendChild(option);
    });

    const input = document.createElement('input');
    input.type = 'number';
    input.step = '0.1';
    input.min = '0.1';
    input.value = '1';
    input.style.marginLeft = '10px';

    const delBtn = document.createElement('button');
    delBtn.textContent = 'Delete';
    delBtn.style.marginLeft = '10px';
    delBtn.className = 'btn btn-secondary';
    delBtn.onclick = () => container.remove();

    container.appendChild(select);
    container.appendChild(input);
    container.appendChild(delBtn);

    document.getElementById('command-list').appendChild(container);
}

function executeCommands() {
    if (isExecuting) return;

    const items = document.querySelectorAll('.command-item');
    const commands = [];

    items.forEach(item => {
        const dir = item.querySelector('select').value;
        const delay = parseFloat(item.querySelector('input').value);
        if (!isNaN(delay) && delay > 0) {
            commands.push({ direction: dir, duration: delay });
        }
    });

    if (commands.length === 0) {
        alert("No valid commands.");
        return;
    }

    isExecuting = true;
    executeSequentially(commands, 0);
}

function executeSequentially(commands, index) {
    if (!isExecuting || index >= commands.length) {
        isExecuting = false;
        return;
    }

    const cmd = commands[index];
    sendCommand(cmd.direction);

    currentTimeout = setTimeout(() => {
        executeSequentially(commands, index + 1);
    }, cmd.duration * 1000);
}

function stopExecution() {
    isExecuting = false;
    if (currentTimeout) clearTimeout(currentTimeout);
}

function saveCommands() {
    const items = document.querySelectorAll('.command-item');
    const commands = [];

    items.forEach(item => {
        const dir = item.querySelector('select').value;
        const duration = parseFloat(item.querySelector('input').value);
        if (!isNaN(duration) && duration > 0) {
            commands.push({ direction: dir, duration: duration });
        }
    });

    if (commands.length === 0) {
        alert("No valid commands to save.");
        return;
    }

    const name = prompt("Enter a name for this command sequence:");
    if (!name) return;

    let saved = JSON.parse(localStorage.getItem('savedCommandsSets') || '{}');
    saved[name] = commands;
    localStorage.setItem('savedCommandsSets', JSON.stringify(saved));

    alert(`Commands saved as "${name}"`);
}

function loadCommands() {
    const saved = JSON.parse(localStorage.getItem('savedCommandsSets') || '{}');
    const names = Object.keys(saved);

    if (names.length === 0) {
        alert('No saved command sets found.');
        return;
    }

    const name = prompt("Enter the name of the command set to load:\n\n" + names.join('\n'));
    if (!name || !saved[name]) {
        alert("Invalid selection.");
        return;
    }

    const commands = saved[name];
    const list = document.getElementById('command-list');
    list.innerHTML = '';

    commands.forEach(cmd => {
        const container = document.createElement('div');
        container.className = 'command-item';
        container.style.marginBottom = '5px';

        const select = document.createElement('select');
        ['FORWARD', 'BACKWARD', 'LEFT', 'RIGHT'].forEach(dir => {
            const option = document.createElement('option');
            option.value = dir;
            option.textContent = dir;
            if (dir === cmd.direction) option.selected = true;
            select.appendChild(option);
        });

        const input = document.createElement('input');
        input.type = 'number';
        input.step = '0.1';
        input.min = '0.1';
        input.value = cmd.duration;
        input.style.marginLeft = '10px';

        const delBtn = document.createElement('button');
        delBtn.textContent = 'Delete';
        delBtn.style.marginLeft = '10px';
        delBtn.onclick = () => container.remove();

        container.appendChild(select);
        container.appendChild(input);
        container.appendChild(delBtn);

        list.appendChild(container);
    });
}

function deleteSavedCommands() {
    const saved = JSON.parse(localStorage.getItem('savedCommandsSets') || '{}');
    const names = Object.keys(saved);

    if (names.length === 0) {
        alert('No saved sets to delete.');
        return;
    }

    const name = prompt("Enter the name of the command set to delete:\n\n" + names.join('\n'));
    if (!name || !saved[name]) {
        alert("Invalid name.");
        return;
    }

    delete saved[name];
    localStorage.setItem('savedCommandsSets', JSON.stringify(saved));
    alert(`Deleted command set "${name}".`);
}

socket.on('device_list_update', function(deviceIds) {
    console.log("device list update")
    const selector = document.getElementById('deviceSelector');
    const currentValue = selector.value;

    selector.innerHTML = '';

    deviceIds.forEach(id => {
        const option = document.createElement('option');
        option.value = id;
        option.textContent = id;
        selector.appendChild(option);
    });

    if (deviceIds.includes(currentValue)) {
        selector.value = currentValue;
    } else if (deviceIds.length > 0) {
        selector.value = deviceIds[0];
    }
});

/*socket.on('device_firestore_check', async function(deviceId) {
    console.log("attempting to check device in firestore")
    try {
        const docRef = db.collection("devices").doc(deviceId);
        const doc = await docRef.get();

        if (!doc.exists) {
            await docRef.set({
                device_id: deviceId,
                isOnline: false,
                isActive: false
            });
            console.log("New device stored in Firestore:", deviceId);
        }
    } catch (error) {
        console.error("Error handling Firestore device:", deviceId, error);
    }
});*/

document.addEventListener('keydown', function(event) {
  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
    event.preventDefault();
  }

  switch (event.key) {
    case 'ArrowUp':
      sendCommand('FORWARD');
      break;
    case 'ArrowDown':
      sendCommand('BACKWARD');
      break;
    case 'ArrowLeft':
      sendCommand('LEFT');
      break;
    case 'ArrowRight':
      sendCommand('RIGHT');
      break;
  }
});

//AUDIO RECEIVER
socket.on('audio_received', function(data) {
    const selectedDeviceId = document.getElementById('deviceSelector').value;

    if (data.device_id === selectedDeviceId) {
        const audioElement = document.getElementById('esp-audio');
        const audioData = 'data:audio/wav;base64,' + data.audio;
        audioElement.src = audioData;
        audioElement.play();
    }
});


