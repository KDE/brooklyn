# Chat Bridge

Chat Bridge is intended to be a protocol-independent chat-bridge to allow messages forward to and from various chat systems.

It supports Telegram and IRC for now but it's modular.

## How to build

Chat Bridge is written in Java and uses Maven to manage dependencies.

You can import the source in your favorite IDE in order to test it.

In production you should create a .jar and use it.

## How to run it

The application needs a config file.

The default path is `/etc/chat-bridge/conf.yml` but you can define a custom path, passing it as a first parameter (e.g. `java -jar chat-bridge.jar /new/path`).

## conf.yml

This is a self-explanatory example of the configuration file.

```yaml
bots:
 tbot:
    type: TelegramBot
    username: "JhonBot"
    token: "blablabla"
 ibot:
    type: IrcBot
    username: "skynet"
    host: "url.of.the.host.com"
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
```

## License

This software is released under the GNU AGPL license.