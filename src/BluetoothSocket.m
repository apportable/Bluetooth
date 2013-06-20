#import "BluetoothSocket.h"
#import "GNUstepBase/GSMime.h"

@implementation BluetoothSocket

@synthesize userData;

- (id)initWithName:(NSString *)name withConnection:(BluetoothConnectionManager *)connection
{
    if ((self = [super init]) ) {
        _name = name;
        _connection = connection;
        self.readQueue = [[BluetoothReadQueue alloc] init];
        self.receivedBuffer = [[NSMutableData alloc] init];
        [[_connection socketMap] setObject:self forKey:name];
    }

    return self;
}

- (void) dealloc
{
    [[_connection socketMap] removeObjectForKey:self.name];
    [_receivedBuffer release];
    [_readQueue release];
    [super dealloc];
}


- (void)writeData:(NSData *)data withTimeout:(NSTimeInterval)timeout tag:(long)tag
{
    NSLog(@"packet is %@ device is %@", data, self.name);
    NSMutableData *dataWithTag = [NSMutableData dataWithBytes:&tag length:sizeof(long)];
    [dataWithTag appendData:data];
    NSString *message = [GSMimeDocument encodeBase64ToString:dataWithTag];
    NSLog(@"%p message is %@ clientDevice is %@", self, message, self.name);
    [_connection sendDeviceMessage:self withMessage:message withTag:tag];
}


- (void)readDataToLength:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag;
// TODO timeout and input tag
{
    int needed = length;
    DEBUG_LOG("length is %d", length);
    if ([_receivedBuffer length] < needed) {
        @synchronized(self) {
            [_readQueue enqueue:needed withTimeout:timeout tag:tag];
        }
        DEBUG_LOG("xyzzy socket.enqueue %d", needed);
    } else {
        DEBUG_LOG("direct returnRead call");
        [_connection returnRead:length fromSocket:self tag:tag];
    }
}

// Only for clients
- (bool)isConnected
{
    return [_connection isConnectedToServer];
}

- (void)disconnect
{
    [[_connection socketMap] removeObjectForKey:_name];
    [_connection disconnect];
}

@end