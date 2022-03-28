from PIL import Image

# row determines which LEDs are selected by the shift register
# this is stored in r
# column determines in what order pixels are sent to the LED driver chip
# this is stored in g

# information contained is (led, skip, led)
matrix = (
    (8, 2, 0),
    (7, 3, 0),
    (6, 4, 0),
    (5, 4, 1),
    (4, 4, 2),
    (3, 4, 3),
    (2, 4, 4),
    (1, 4, 5),
    (0, 4, 6),  # this adds on to row 6
    (0, 3, 7),  # this adds on to row 7
)
# 8 collumns, 8 rows total

# map fits nicely in 320x180 video resolution
(WIDTH, HEIGHT) = (320, 180)

img = Image.new("RGB", (WIDTH, HEIGHT), (255, 255, 255))  # default to white background

pixels = img.load()

numleds = 0

# chunks are described by the matrix tuple above
# each block is composed of 4 chunks as such:
# [/] [\]   =   normal, flip horizontal
# [\] [/]   =   flip horizontal, normal
#    ^  space of 3 leds in between

# the whole image is 4 sections, each a 4x5 array of blocks


def doSection(posOffset, colOffset, sleeve):
    workingat = list(posOffset)

    c = colOffset

    for blockx in range(5):
        blockCol = 0
        for blocky in range(4):
            workingat[0] = posOffset[0] + blockx * 26
            workingat[1] = posOffset[1] + blocky * 20

            doBlock(workingat, blocky * 16, c, sleeve)

        c += 16


def doBlock(posOffset, rowOffset, colOffset, sleeve):
    # top left chunk
    basey = posOffset[1]
    basex = posOffset[0]

    doChunk(basex, basey, rowOffset, colOffset, sleeve, False)

    # top right chunk
    # basey = posOffset[1]
    basex += 13

    doChunk(basex, basey, rowOffset, colOffset + 8, sleeve, True)

    # bottom left chunk
    basey += 10
    basex = posOffset[0]

    doChunk(basex, basey, rowOffset + 8, colOffset, sleeve, True)

    # bottom right chunk
    basey = posOffset[1] + 10
    basex += 13

    doChunk(basex, basey, rowOffset + 8, colOffset + 8, sleeve, False)


def doChunk(basex, basey, rowOffset, colOffset, sleeve, flip):
    global numleds

    if flip:
        first = 2
        last = 0
        firstoffset = -2
        lastoffset = 0
    else:
        first = 0
        last = 2
        firstoffset = 0
        lastoffset = -2

    y = basey
    for row in range(len(matrix)):
        x = basex
        c = 0

        for led in range(matrix[row][first]):
            try:
                pixels[x, y] = (rowOffset + row + firstoffset, colOffset + c, sleeve)
                numleds += 1
            except:
                print("Out of bounds: " + str(x) + ", " + str(y))
            x += 1
            c += 1

        # skip
        x += matrix[row][1]

        for led in range(matrix[row][last]):
            # the second row of leds is always offset by one
            try:
                pixels[x, y] = (rowOffset + row + lastoffset, colOffset + c, sleeve)
                numleds += 1
            except:
                print("Out of bounds: " + str(x) + ", " + str(y))
            x += 1
            c += 1

        y += 1


# generate the full image
# there are 16*5=80 rows in a section

doSection((26, 4), 0, 0)
doSection((162, 4), 80, 0)
doSection((26, 94), 0, 1)
doSection((162, 94), 80, 1)

# maximums for r (row), g (column), b (sleeve): (32, 160, 1)

print(str(numleds) + " leds / active pixels in total")

# img.save("..\\app\\src\\main\\res\\drawable-nodpi\\map.bmp")

# put the resulting image in \app\src\main\res\drawable-nodpi

img.save("map.bmp")

# make it nice for viewing (normalize r g b)
for y in range(HEIGHT):
    for x in range(WIDTH):
        (r, g, b) = pixels[x, y]
        pixels[x, y] = (int(r * 256 / 36), g, b * 256)


img.show()
img.save("mapPreview.png")
