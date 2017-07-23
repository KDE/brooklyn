# Brooklyn

Brooklyn is intended to be a protocol-independent chat-bridge to allow messages forward to and from various chat systems.

It supports Telegram and IRC for now but it's modular.

## How to build

Brooklyn is written in Java and uses Maven to manage dependencies.

You can import the source in your favorite IDE in order to test it.

In production you should create a .jar and use it.

## How to run it

The application needs a config file.

The default path is `/etc/brooklyn/conf.yml` but you can define a custom path, passing it as a first parameter (e.g. `java -jar brooklyn.jar /new/path`).

### Telegram bot

Due to [Telegram APIs limit](https://core.telegram.org/bots/faq#broadcasting-to-users)
you should create one bot for each telegram-related channel.

Remember to add the bot on each group you want to manage before adding it in your config file.

### Rocket.Chat bot

Use secure websocket (wss) instead of ws to connect the bot to a server,
because login passwords are sent in plain text.

Remember to add the bot on each channel you want to manage before adding it in your config file.


### conf.yml

This is a self-explanatory example of the configuration file.

```yaml
bots:
 tbot:
    type: TelegramBot
    username: "JhonBot"
    password: "blablabla" # The token key
 ibot:
    type: IrcBot
    username: "skynet"
    password: "123456" # Optional
    host: "url.of.the.host.com"
 rbot:
    type: RocketChatBot
    host: "wss://chat.wikitolearn.org"
    username: "username"
    password: "password"
channels:
  ch1:
    bot: ibot
    name: "#channelname"
  ch2:
    bot: tbot
    name: "chat-id"
bridges:
  -
    - ch1
    - ch2
webserver:
  content-folder: "/var/www/html/"
  base-url: "http://localhost/"
db-uri: "jdbc:sqlite:" # You should not specify a path, so the file is temporary.

```

### Webserver

You need also a webserver (e.g. nginx) to support attachments in protocols like IRC.

Don't forget to add read and write permissions for the content-folder.
Otherwise the app won't work.

You should create a Cron job to remove images when you're running out of space.

An example might be

```python
import os
import shutil
from datetime import date

contentFolder = "/var/www/html/"  # Folder to clean
maxSizeGB = 4  # Max folder size
nDaysBeforeExpiration = 1

# Get dir size
dirSizeBytes = 0
for dirPath, dirNames, fileName in os.walk(contentFolder):
    for f in fileName:
        fp = os.path.join(dirPath, f)
        dirSizeBytes += os.path.getsize(fp)

# Clean dir if it is too big
maxSizeBytes = maxSizeGB * 1024 * 1024 * 1024;
if dirSizeBytes > maxSizeBytes:
    # Iterate through years
    nYearsBeforeExpiration = nDaysBeforeExpiration / 30 / 12
    for d in os.listdir(contentFolder):
        fullDirPath = contentFolder + d
        if os.path.isdir(fullDirPath):
            if date.today().year - nYearsBeforeExpiration > int(d):
                print("Deleting " + fullDirPath)
                shutil.rmtree(fullDirPath)

    # Iterate through months
    nMonthsBeforeExpiration = nDaysBeforeExpiration / 30
    yearFolder = contentFolder + str(date.today().year).zfill(4)
    for d in os.listdir(yearFolder):
        fullDirPath = os.path.join(yearFolder, d)
        if os.path.isdir(fullDirPath):
            if date.today().month - nMonthsBeforeExpiration > int(d):
                print("Deleting " + fullDirPath)
                shutil.rmtree(fullDirPath)

    # Iterate through days
    monthFolder = os.path.join(yearFolder, str(date.today().month).zfill(2))
    for d in os.listdir(monthFolder):
        fullDirPath = os.path.join(monthFolder, d)
        if os.path.isdir(fullDirPath):
            if date.today().day - nDaysBeforeExpiration > int(d):
                print("Deleting " + fullDirPath)
                shutil.rmtree(fullDirPath)
else:
    print("Nothing to clean, there is enough space.")

```

## License

This software is released under the GNU AGPL license.