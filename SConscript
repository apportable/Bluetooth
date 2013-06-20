header_paths = [
]

defines = {
}

flags = [

]

deps = [
    'bluetooth/google',
]

sources = [
	'src/BluetoothSocket.m',
    'src/BluetoothConnectionManager.m',
    'src/BluetoothReadQueue.m',
]

Import('env')
env.BuildLibrary('bluetooth', sources, header_paths=header_paths, static=True, flags=flags, defines=defines, deps=deps)
