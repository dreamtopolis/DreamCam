DreamCam

A camera system plugin that allows players to create and view fixed camera positions throughout your Minecraft world. Create cameras at any location, organize them by regions, and switch between them seamlessly.
Features

    Create cameras at any location with saved position and direction
    Organize cameras by regions for better management
    Interactive GUI menu to browse and select cameras
    Navigate between cameras using left/right click
    Automatic night vision while viewing (configurable)
    Position freeze - look around but cannot move
    Automatic spectator mode for optimal viewing
    Fully customizable messages and settings
    Persistent storage - cameras save automatically

Commands
Command 	Description 	Permission
/camera create <name> <region> 	Create a new camera at your location 	dreamcam.admin.create
/camera delete <name|region> 	Delete a camera or all cameras in a region 	dreamcam.admin.delete
/camera menu <region> 	Open the camera menu for a region 	dreamcam.use
/camera reload 	Reload the plugin configuration 	dreamcam.admin.reload
/camera save 	Save all cameras to config 	dreamcam.admin.save
/camera load 	Load cameras from config 	dreamcam.admin.reload
Permissions
Permission 	Description 	Default
dreamcam.* 	Grants all DreamCam permissions 	op
dreamcam.use 	Allows using cameras and opening camera menus 	true
dreamcam.admin 	Grants all admin permissions 	op
dreamcam.admin.create 	Allows creating new cameras 	op
dreamcam.admin.delete 	Allows deleting cameras 	op
dreamcam.admin.reload 	Allows reloading the configuration 	op
dreamcam.admin.save 	Allows saving cameras to config 	op
Usage
Create

Stand at your desired camera position and look in the direction you want the camera to face. Run /camera create <camera-name> <region-name> to create the camera. The camera will save your exact position and viewing direction.
View

Run /camera menu <region-name> to open the camera selection menu. Click any camera to start viewing. While viewing, right-click to switch to the next camera, left-click for the previous camera, and press shift to exit camera mode.
Manage

Delete individual cameras with /camera delete <camera-name> or remove all cameras in a region with /camera delete <region-name>. Use /camera reload to reload configuration changes.
Configuration

The plugin is fully configurable through config.yml:

camera:
  gamemode: SPECTATOR              # Game mode while viewing
  night-vision:
    enabled: true                  # Enable night vision effect
    duration: 72000                # Duration in ticks
    amplifier: 0                   # Effect level
  freeze-position: true            # Prevent movement while viewing
  menu:
    material: BLUE_CONCRETE        # Menu icon material

All messages can be customized in messages.yml with color code support.
Details

    API Version: 1.21
    Compatible with: Spigot, Paper, and forks
    Java Version: 8+
    Dependencies: None
    Performance: Lightweight with minimal impact

Use Cases

    Security - Monitor important areas from fixed viewpoints
    Events - Capture different angles during server events
    Showcases - Display builds from perfect camera angles
    Mini-games - Spectate games from strategic camera positions
    Themeparks - Let players see guests in the queue or on the attraction

Installation

    Download DreamCam from Modrinth
    Place the JAR file in your server's plugins folder
    Restart your server
    Configure permissions as needed
    Start creating cameras

License

This project is licensed under the MIT License.
