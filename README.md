## A lean matrix client 

Purpose of this project is diving into Android development using Kotlin as well
as attempting to contribute to the backend project on which this application is built on `rust-matrix-sdk`.
Possibly meant as a toy project, or a complete implementation of a chat client; I don't know right now.
But seeing how far I've gotten in just a few weeks, with no prior Android (or Kotlin) experience, who knows?

[Conventions and & ideology](Conventions&Ideology.md), like when to use and when not to use dependencies, "rules of thumb", etc 

## Current state

### TODO
 - Add more README content, the usual suspects stuff
 - Read & update [research document](RESEARCH.md) continuously

### Feature implementation

#### General & Utility
- [x] [Login using username & password](app/src/main/java/com/app/radiator/ui/routes/Login.kt)
- [ ] Login using SSO
- [ ] Create room
- [ ] Delete room(?)
- [x] [Display loading animation](app/src/main/java/com/app/radiator/ui/components/LoadingAnimation.kt)
- [ ] Verify session
- [ ] Application settings
- [ ] Run in background
  - [ ] Notify user of updates
- [ ] Persistent storage 
  - [ ] Serialize cached images
  - [ ] Compression & decompression of serialized data
- [ ] Leave room
- [ ] Join room
- [ ] Feature settings; being able to turn things on or off, like syntax highlighting of code blocks (to name a trivial example), no avatars, "lean mode" etc

#### In-room features
- [ ] Room settings
- [x] Search room for text ([composable](app/src/main/java/com/app/radiator/ui/routes/Room.kt#L128-169), [implementation](app/src/main/java/com/app/radiator/ui/routes/Room.kt#L370-394))
- [x] [Display replies](app/src/main/java/com/app/radiator/ui/components/ReplyItem.kt#L94)
- [ ] Reply to messages
- [ ] User invite
- [ ] Room management, banning users, changing topic
- [x] [Initial, stupid HTML parsing implementation](app/src/main/java/com/app/radiator/matrix/htmlparse/Parser.kt)
- [ ] Add attachment
- [ ] Being able to download files 
- [x] Open http link in default browser
- [x] [Parse code block HTML](app/src/main/java/com/app/radiator/matrix/htmlparse/Parser.kt#L400-513)
- [x] [Code block display](app/src/main/java/com/app/radiator/ui/components/ParsedMessageNode.kt#L198-221)
- [ ] Syntax highlighting for code blocks
- [ ] Send read receipts

##### Media
- [x] [Asynchronously loading the images](app/src/main/java/com/app/radiator/matrix/store/AsyncImage.kt) 
- [x] [Caching images to volatile storage](app/src/main/java/com/app/radiator/matrix/store/AsyncImage.kt)
- [x] [Displaying avatars (users and room)](app/src/main/java/com/app/radiator/ui/components/RowItem.kt#L22-38)
- [ ] Display emojis
- [ ] Record audio
- [ ] Record video