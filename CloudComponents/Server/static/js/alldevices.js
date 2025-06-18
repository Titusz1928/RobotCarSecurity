const socket = io();
const deviceGrid = document.getElementById('all-devices-grid');
const noDevicesMessage = document.getElementById('no-devices-message');
const deviceImages = {};

let deviceReceived = false;


// Firebase config
  const firebaseConfig = {
    apiKey: "",
    authDomain: "",
    projectId: "",
    storageBucket: "",
    messagingSenderId: "",
    appId: ""
  };

  // Initialize Firebase
  const app = firebase.initializeApp(firebaseConfig);
  const db = firebase.firestore();

socket.emit('get_firestore_devices');

function hideNoDevicesMessage() {
  if (!deviceReceived) {
    noDevicesMessage.style.display = 'none';
    deviceReceived = true;
  }
}

// shows "no devices" after 3s
setTimeout(() => {
  if (!deviceReceived) {
    noDevicesMessage.style.display = 'block';
  }
}, 3000);

// device card adder function
function createDeviceCard(device_id, imageSrc) {
  const wrapper = document.createElement('div');
  wrapper.className = 'device-wrapper';

  const title = document.createElement('p');
  title.innerText = "Device: " + device_id;
  title.className = 'device-title';

  const img = document.createElement('img');
  img.id = `img-${device_id}`;
  img.className = 'device-image';
  img.src = imageSrc;

  const link = document.createElement('a');
  link.href = `/control?device_id=${encodeURIComponent(device_id)}`;
  link.appendChild(img);

  wrapper.appendChild(title);
  wrapper.appendChild(link);
  deviceGrid.appendChild(wrapper);

  deviceImages[device_id] = img;
}

// Handle image update
socket.on('image_update', function(data) {
  //console.log("[all-devices] image_update received", data);
  const { device_id, image } = data;

  hideNoDevicesMessage();

  const imageSrc = 'data:image/jpeg;base64,' + image;

  if (!deviceImages[device_id]) {
    createDeviceCard(device_id, imageSrc);
  } else {
    deviceImages[device_id].src = imageSrc;
  }
});

socket.on('device_firestore_check', async function(deviceId) {
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
});


//DEVICE SERVER SETTINGS FUNCTION
async function loadFirestoreDevices() {
  console.log("loading device settings from firestore");

  const devicesCol = db.collection("devices");
  const devicesSnapshot = await devicesCol.get();

  let tableHTML = `
    <table border="1" style="width:100%; border-collapse: collapse;">
      <thead>
        <tr>
          <th>Device ID</th>
          <th>Lane Assist</th>
          <th>Person Detection</th>
        </tr>
      </thead>
      <tbody>
  `;

  let rowCount = 0;

  devicesSnapshot.forEach(doc => {
    const data = doc.data();
    const deviceId = data.device_id;
    tableHTML += `
      <tr data-device-id="${deviceId}">
        <td>${deviceId}</td>
        <td><input type="checkbox" class="laneAssist" ${data.laneAssist ? 'checked' : ''} /></td>
        <td><input type="checkbox" class="personDetection" ${data.personDetection ? 'checked' : ''} /></td>
      </tr>
    `;
    rowCount++;
  });

  tableHTML += '</tbody></table>';

  if (rowCount > 0) {
    tableHTML += `
      <div style="margin-top: 10px;">
        <button class="btn btn-info" id="save-devices-btn">Save</button>
        <div id="save-message" style="margin-top: 8px; color: green; display: none;"></div>
      </div>
    `;
  }

  document.querySelector('#device-settings-table-container').innerHTML = tableHTML;

  const saveBtn = document.getElementById('save-devices-btn');
  if (saveBtn) {
    saveBtn.addEventListener('click', async () => {
      console.log('Save button clicked');
       const saveMessage = document.getElementById('save-message');
        saveMessage.style.display = 'none';

      const rows = document.querySelectorAll('#device-settings-table-container table tbody tr');

      for (const row of rows) {
        const deviceId = row.getAttribute('data-device-id');
        const laneAssist = row.querySelector('input.laneAssist').checked;
        const personDetection = row.querySelector('input.personDetection').checked;

        try {
          // Updates Firestore
          await db.collection("devices").doc(deviceId).update({
            laneAssist,
            personDetection
          });
          console.log(`Updated device ${deviceId} successfully`);
        } catch (error) {
          console.error(`Failed to update device ${deviceId}:`, error);
            saveMessage.style.color = 'red';
            saveMessage.textContent = `Error updating device ${deviceId}`;
            saveMessage.style.display = 'block';
            return;
        }
      }

      saveMessage.style.color = 'green';
        saveMessage.textContent = 'Changes saved successfully!';
        saveMessage.style.display = 'block';


        // Hides message after 3 seconds
        setTimeout(() => {
          saveMessage.style.display = 'none';
        }, 3000);

        socket.emit('reload_firestore_devices');

      //loadFirestoreDevices();
    });
  }
}


socket.on('get_firestore_devices', loadFirestoreDevices);

window.addEventListener('DOMContentLoaded', () => {
  socket.emit('get_firestore_devices');
  loadFirestoreDevices();
});

// Handles audio received
socket.on('audio_received', function(data) {
  console.log("[all-devices] audio_received from", data.device_id);
  const { device_id } = data;

  hideNoDevicesMessage();

  // If device card not created yet (no image), use default image
  if (!deviceImages[device_id]) {
    createDeviceCard(device_id, 'static/noconnectionsmall.png');
  }
});
