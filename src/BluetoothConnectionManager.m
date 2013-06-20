
#import "BluetoothConnectionManager.h"
#import "BluetoothSocket.h"
#import <BridgeKit/AndroidActivity.h>
#import <BridgeKit/AndroidContext.h>
#import "GNUstepBase/GSMime.h"
#import <UIKit/UIAlertView.h>

#define TAG_LENGTH sizeof(long)

@interface BluetoothConnectionManager() {
@public
    // BOOL _inRestoreState;

@private
    // NSMutableData *_receivedBuffer;
    // int _pendingReadLength;
}
- (id)_initWithContext:(AndroidContext *)ctx name:(NSString *)name isClient:(BOOL)isClient;
+ (BOOL)_isAvailable;
- (NSString *)_getAddress;
- (void)_startPublishing;
- (void)_stopPublishing;
- (void)_startSearching;
- (void)_disconnect;
- (void)_shutdown;
- (void)_sendDeviceMessage:(NSString *)device message:(NSString *)message;
- (bool)_isConnectedToServer;

@property (nonatomic, readonly) id delegate;
@property (readonly) bool isClient;
@end

@protocol Delegate <NSObject>
@optional
- (void)didConnectToServer:(BluetoothSocket *)server;
- (void)connectionReceived:(BluetoothSocket *)clientDevice;
@end


@protocol VirtualSocketDelegate <NSObject>  // Should stay in sync with GCDAsyncSocketDelegate
- (dispatch_queue_t)newSocketQueueForConnectionFromAddress:(NSData *)address onSocket:(id <VirtualSocket>)sock;
- (void)socket:(id <VirtualSocket>)sock didAcceptNewSocket:(id <VirtualSocket>)newSocket;
- (void)socket:(id <VirtualSocket>)sock didConnectToHost:(NSString *)host port:(uint16_t)port;
- (void)socket:(id <VirtualSocket>)sock didReadData:(NSData *)data withTag:(long)tag;
- (void)socket:(id <VirtualSocket>)sock didWriteDataWithTag:(long)tag;
- (NSTimeInterval)socket:(id <VirtualSocket>)sock shouldTimeoutReadWithTag:(long)tag
                                                                 elapsed:(NSTimeInterval)elapsed
                                                               bytesDone:(NSUInteger)length;
- (NSTimeInterval)socket:(id <VirtualSocket>)sock shouldTimeoutWriteWithTag:(long)tag
                                                                  elapsed:(NSTimeInterval)elapsed
                                                                bytesDone:(NSUInteger)length;
- (void)socketDidCloseReadStream:(id <VirtualSocket>)sock;
- (void)socketDidDisconnect:(id <VirtualSocket>)sock;
@end


@implementation BluetoothConnectionManager

@synthesize delegate;
@synthesize socketMap;
@synthesize isClient;

// static BOOL isClientConnected;
// static BOOL isServerConnected;

+ (void)initializeJava
{
    [super initializeJava];
    [BluetoothConnectionManager registerConstructorWithSelector:@selector(_initWithContext:name:isClient:) arguments:[AndroidContext className], 
        [NSString className], [JavaClass boolPrimitive], nil];
    [BluetoothConnectionManager registerStaticMethod:@"isAvailable" selector:@selector(_isAvailable) returnValue:[JavaClass boolPrimitive] arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"getAddress" selector:@selector(_getAddress) returnValue:[NSString className] arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"startPublishing" selector:@selector(_startPublishing) returnValue:nil arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"stopPublishing" selector:@selector(_stopPublishing) returnValue:nil arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"startSearching" selector:@selector(_startSearching) returnValue:nil arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"disconnect" selector:@selector(_disconnect) returnValue:nil arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"shutdown" selector:@selector(_shutdown) returnValue:nil arguments:nil];
    [BluetoothConnectionManager registerInstanceMethod:@"sendDeviceMessage" selector:@selector(_sendDeviceMessage:message:) returnValue:nil 
        arguments:[NSString className], [NSString className], nil];
    [BluetoothConnectionManager registerInstanceMethod:@"isConnectedToServer" selector:@selector(_isConnectedToServer) returnValue:[JavaClass boolPrimitive] arguments:nil];
    [BluetoothConnectionManager registerCallback:@"connectionReceived"
                            selector:@selector(connectionReceived:) 
                            returnValue:nil
                            arguments:[NSString className], nil];
    [BluetoothConnectionManager registerCallback:@"didPublish"
                            selector:@selector(didPublish) 
                            returnValue:nil
                            arguments:nil];
    [BluetoothConnectionManager registerCallback:@"didStopPublishing"
                            selector:@selector(didStopPublishing) 
                            returnValue:nil
                            arguments:nil];
    [BluetoothConnectionManager registerCallback:@"didConnectToServer"
                            selector:@selector(didConnectToServer:) 
                            returnValue:nil
                            arguments:[NSString className], nil];
    [BluetoothConnectionManager registerCallback:@"didReceive"
                            selector:@selector(didReceive:fromDevice:) 
                            returnValue:nil
                            arguments:[NSString className], [NSString className], nil];
    [BluetoothConnectionManager registerCallback:@"didDisconnect"
                            selector:@selector(didDisconnect:) 
                            returnValue:nil
                            arguments:[NSString className], nil];
    [BluetoothConnectionManager registerCallback:@"resetBluetoothNeeded"
                            selector:@selector(resetBluetoothNeeded) 
                            returnValue:nil
                            arguments:nil];

                            // [ApportableIAP registerInstanceMethod:@"purchaseObject" selector:@selector(_purchaseObjectWithProductIdentifier:requestID:) returnValue:[JavaClass boolPrimitive] arguments:[NSString className],[NSString className], nil];
    // [ApportableIAP registerInstanceMethod:@"confirmNotification" selector:@selector(confirmNotification:startId:) arguments:[NSString className], [JavaClass intPrimitive], nil];
    // [ApportableIAP registerInstanceMethod:@"checkBillingSupported" selector:@selector(checkBillingSupported) returnValue:[JavaClass boolPrimitive]];
 //    [ApportableIAP registerInstanceMethod:@"restoreTransactions" selector:@selector(_restoreTransactions) returnValue:[JavaClass boolPrimitive]];
}

+ (BOOL) isAvailable {
    BOOL avail = [BluetoothConnectionManager _isAvailable];
    DEBUG_LOG("bluetooth_available is %d", avail);
    return avail;
}

- (id)initWithName:(NSString *)name isClient:(BOOL)isC delegate:(id)dgt {
    DEBUG_LOG("starting objc init_bluetooth");
    if (![BluetoothConnectionManager isAvailable]) {
        return nil;
    }
    self = [self _initWithContext:[AndroidActivity currentActivity] name:name isClient:isC];
    if (self)
    {
        delegate = dgt;
        isClient = isC;
    }
    NSLog(@"%p", self);
    return self;
}

- (NSString *)getAddress {
    return [self _getAddress];
}
- (void)startPublishing {
    [self _startPublishing];
}

- (void)stopPublishing {
    [self _stopPublishing];
}

- (void)startSearching {
            DEBUG_LOG("calling startSearching before bridge");
    [self _startSearching];
}

- (void)disconnect {
    NSLog(@"objc disconnect");
    [self _disconnect];
}

- (void)shutdown {
    NSLog(@"objc shutdown");
    [self.socketMap release];
    self.socketMap = nil;

//     if (self.isClient)
//     {
//         isClientConnected = NO;
//     }
//     else
//     {
//         isServerConnected = NO;
//     }
// //  only shutdown if both server and client are done
//     if (isServerConnected == NO && isClientConnected == NO)
    {
        [self _shutdown];
    }  
}

- (bool)isConnectedToServer {
    return [self _isConnectedToServer];
}

- (void)sendDeviceMessage:(BluetoothSocket *)device withMessage:(NSString *)message withTag:(long)tag {
    [self _sendDeviceMessage:device.name message:message];
    if ([self.delegate respondsToSelector:@selector(socket:didWriteDataWithTag:)])
    {
        [self.delegate socket:device didWriteDataWithTag:tag];
    }
}

- (void)connectionReceived:(JavaObject *)msg
{
    NSString *clientDevice = [NSString stringWithJavaString:(jstring)msg->_object];
    dispatch_async(dispatch_get_main_queue(), ^{
//        isServerConnected = YES;
        NSLog(@"in connectionReceived %@", clientDevice);
        DEBUG_LOG("class is %s", class_getName(object_getClass(clientDevice)));
        self.socketMap = [[NSMutableDictionary alloc] init];
        if ([self.delegate respondsToSelector:@selector(connectionReceived:)])
        {
            [self.delegate connectionReceived:[[BluetoothSocket alloc] initWithName:clientDevice withConnection:self]];
        }
    });
}


- (void)didPublish
{
    dispatch_async(dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(didPublish)])
        {
            [self.delegate didPublish];
        }
    });
}

- (void)didStopPublishing
{
    dispatch_async(dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(didStopPublishing)])
        {
            [self.delegate didStopPublishing];
        }
    });
}

- (void)didConnectToServer:(JavaObject *)msg
{
    NSString *serverDevice = [NSString stringWithJavaString:(jstring)msg->_object];

    dispatch_async(dispatch_get_main_queue(), ^{
//        isClientConnected = YES;
        NSLog(@"in didConnectToServer %@", serverDevice);
        self.socketMap = [[NSMutableDictionary alloc] init];
        if ([self.delegate respondsToSelector:@selector(didConnectToServer:)])
        {
            [self.delegate didConnectToServer:[[BluetoothSocket alloc] initWithName:serverDevice withConnection:self]];
        }
    });
}

-(void)returnRead:(int)length fromSocket:(BluetoothSocket *)socket tag:(long)requestedTag
{
    NSData *readBuffer;
    @synchronized(socket.receivedBuffer) {
        unsigned char *bytes = (unsigned char *)[socket.receivedBuffer bytes];
        long readTag = *(int *)bytes;
        NSRange range = NSMakeRange(0, TAG_LENGTH);
        if (readTag == requestedTag) {
            // Then the data starts after the tag
            [socket.receivedBuffer replaceBytesInRange:range withBytes:NULL length:0];
            if ([socket.receivedBuffer length] < length) {
                // No longer enough bytes for request - is this even possible?
                DEBUG_LOG("TO Do - reset readQueue");
                return;
            }
        }

        range = NSMakeRange(0, length);
        DEBUG_LOG("substringing %d to %d expecting tag %lx found tag %lx", [socket.receivedBuffer length], length, requestedTag, readTag);
        readBuffer = [socket.receivedBuffer subdataWithRange:range];
        [socket.receivedBuffer replaceBytesInRange:range withBytes:NULL length:0];
    }

    if ([self.delegate respondsToSelector:@selector(socket:didReadData:withTag:)])
    {
        [self.delegate socket:socket didReadData:readBuffer withTag:requestedTag];
    }
}

- (void)didReceive:(JavaObject *)msg fromDevice:(JavaObject *)dev
{
    NSString *message = [NSString stringWithJavaString:(jstring)msg->_object];
    NSString *device = [NSString stringWithJavaString:(jstring)dev->_object];
    dispatch_async(dispatch_get_main_queue(), ^{
        BluetoothSocket *socket = [self.socketMap valueForKey:device];
        NSMutableData *rawData = [[GSMimeDocument decodeBase64FromString:message multipleChunks:YES] mutableCopy];
        NSLog(@"in didReceive length %d %@ from device %@", [rawData length], rawData, device);
        @synchronized(socket) {
            [socket.receivedBuffer appendData:rawData];
                    NSLog(@"in receeivedBuffer length before loop %d", [socket.receivedBuffer length]);
            while (![socket.readQueue isEmpty] && [socket.readQueue nextLength] <= [socket.receivedBuffer length]) {
                DEBUG_LOG("deferred returnRead call %d from %d", [socket.readQueue nextLength], [socket.receivedBuffer length]);
                long nextTag = [socket.readQueue nextTag];
                [self returnRead:([socket.readQueue dequeue]) fromSocket:socket tag:nextTag];
            }
            NSLog(@"in receeivedBuffer length after loop %d", [socket.receivedBuffer length]);
        }
    });
}


- (void)didDisconnect:(JavaObject *)msg
{
    NSString *device = [NSString stringWithJavaString:(jstring)msg->_object];
    BluetoothSocket *socket = [self.socketMap valueForKey:device];
    [self.socketMap release];
    self.socketMap = nil;

    dispatch_async(dispatch_get_main_queue(), ^{
        NSLog(@"in didDisconnect %@", device);
        if ([self.delegate respondsToSelector: @selector(socketDidDisconnect:)])
        {
            [self.delegate socketDidDisconnect:socket];
        }
    });
}


- (void)resetBluetoothNeeded
{
    dispatch_async(dispatch_get_main_queue(), ^{
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle: @"Bluetooth - Cannot create more connections" 
        message: @"Cycle airplane mode off and on and restart" 
        delegate: nil cancelButtonTitle:@"OK" 
        otherButtonTitles:nil]; 
        [alert show]; 
        [alert release];
    });
}



+(NSString *) className 
{
    return @"com.apportable.bluetooth.BluetoothConnectionManager";
}


@end


